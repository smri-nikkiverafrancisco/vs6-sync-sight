package com.vs6.syncsight.promotions.ongoing.starting.web;

import com.vs6.syncsight.promotions.ongoing.starting.model.StartingPromotion;
import com.vs6.syncsight.promotions.ongoing.starting.model.StartingPromotionStatus;
import com.vs6.syncsight.promotions.ongoing.starting.model.StoreStatusRow;
import com.vs6.syncsight.promotions.shared.anomaly.*;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import com.vs6.syncsight.promotions.ongoing.starting.service.PromotionSyncService;
import com.vs6.syncsight.promotions.ongoing.starting.service.PromotionSyncService.RefreshStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Controller
public class StartingPromotionsController {

    private static final DateTimeFormatter HEADER_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PromotionSyncService syncService;
    private final AnomalyDetector anomalyDetector;

    public StartingPromotionsController(PromotionSyncService syncService, AnomalyDetector anomalyDetector) {
        this.syncService = syncService;
        this.anomalyDetector = anomalyDetector;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/promotions/ongoing/starting";
    }

    // The Refresh button: start the refresh, then come back to the same tab and view
    @PostMapping("/promotions/ongoing/starting/refresh")
    public String refresh(@RequestParam(defaultValue = "all") String tab,
                          @RequestParam(defaultValue = "promo") String view) {
        syncService.startRefresh();
        String safeTab = "anomalies".equals(tab) ? "anomalies" : "all";
        String safeView = "store".equals(view) ? "store" : "promo";
        return "redirect:/promotions/ongoing/starting?tab=" + safeTab + "&view=" + safeView;
    }

    @GetMapping("/promotions/ongoing/starting")
    public String page(@RequestParam(defaultValue = "all") String tab,
                       @RequestParam(defaultValue = "promo") String view,
                       Model model) {
        boolean anomaliesTab = "anomalies".equals(tab);

        Instant refreshed = syncService.getLastRefreshed();
        RefreshStatus status = syncService.getStatus();

        List<StartingPromotionStatus> report = syncService.getStartingReport();

        // "All" tab, both views
        List<PromoView> promos = report.stream().map(this::toView).toList();
        List<StoreGroupView> allStoreGroups = toAllStoreGroups(report);

        // "Anomalies" tab, both views
        List<PromoAnomaly> anomalies = anomalyDetector.detect(report);
        List<AnomalyPromoView> anomalyViews = anomalies.stream().map(this::toAnomalyView).toList();
        List<AnomalyStoreGroupView> anomalyStoreGroups = anomalyDetector.groupByStore(anomalies).stream()
                .map(this::toGroupView).toList();
        long criticalCount = anomalies.stream().filter(a -> a.severity() == Severity.CRITICAL).count();
        long warningCount = anomalies.size() - criticalCount;

        String emptyMessage;
        if (refreshed == null) {
            emptyMessage = status.running()
                    ? "Loading the data... this page reloads by itself."
                    : "No data yet. Click Refresh.";
        } else {
            emptyMessage = anomaliesTab ? "No anomalies found." : "No promotions start today.";
        }

        String refreshText = status.stage()
                + (status.total() > 0 ? " (" + status.done() + "/" + status.total() + " stores)" : "");

        model.addAttribute("tab", anomaliesTab ? "anomalies" : "all");
        model.addAttribute("view", "store".equals(view) ? "store" : "promo");
        model.addAttribute("today", LocalDate.now().format(HEADER_DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? "--:--:--" : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promoCount", promos.size());
        model.addAttribute("promos", promos);
        model.addAttribute("allStoreGroups", allStoreGroups);
        model.addAttribute("anomalyPromos", anomalyViews);
        model.addAttribute("anomalyStoreGroups", anomalyStoreGroups);
        model.addAttribute("criticalCount", criticalCount);
        model.addAttribute("warningCount", warningCount);
        model.addAttribute("nothingToShow", anomaliesTab ? anomalyViews.isEmpty() : promos.isEmpty());
        model.addAttribute("emptyMessage", emptyMessage);
        model.addAttribute("refreshing", status.running());
        model.addAttribute("refreshText", refreshText);
        model.addAttribute("refreshError", status.lastError());
        return "promotions/ongoing/starting";
    }

    // ---------- "All" tab, view "By Promo" ----------

    private PromoView toView(StartingPromotionStatus status) {
        StartingPromotion p = status.promo();
        List<StoreView> stores = status.stores().stream().map(this::toStoreView).toList();
        return new PromoView(p.promoId(), p.memoNumber(), p.storeCount(),
                p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE), p.durationText(), stores);
    }

    private StoreView toStoreView(StoreStatusRow row) {
        return new StoreView(row.storeId(), row.storeName(),
                row.enterprise().label(), statusClass(row.enterprise()),
                row.local().label(), statusClass(row.local()));
    }

    // ---------- "All" tab, view "By Store" ----------

    // Flip the report: from "promos with their stores" to "stores with their promos"
    private List<StoreGroupView> toAllStoreGroups(List<StartingPromotionStatus> report) {
        Map<Integer, List<StorePromoRowView>> byStore = new TreeMap<>();   // sorted by store ID
        Map<Integer, String> names = new HashMap<>();

        for (StartingPromotionStatus entry : report) {
            StartingPromotion p = entry.promo();
            for (StoreStatusRow row : entry.stores()) {
                names.putIfAbsent(row.storeId(), row.storeName());
                byStore.computeIfAbsent(row.storeId(), k -> new ArrayList<>())
                        .add(new StorePromoRowView(p.promoId(), p.memoNumber(),
                                p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE),
                                row.enterprise().label(), statusClass(row.enterprise()),
                                row.local().label(), statusClass(row.local())));
            }
        }

        List<StoreGroupView> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<StorePromoRowView>> e : byStore.entrySet()) {
            List<StorePromoRowView> rows = e.getValue();
            rows.sort(Comparator.comparingInt(StorePromoRowView::promoId));   // by promo ID
            int n = rows.size();
            groups.add(new StoreGroupView(e.getKey(), names.get(e.getKey()),
                    n + (n == 1 ? " promo" : " promos"), rows));
        }
        return groups;
    }

    // ---------- "Anomalies" tab, view "By Promo" ----------

    private AnomalyPromoView toAnomalyView(PromoAnomaly a) {
        StartingPromotion p = a.promo();
        List<AnomalyStoreView> rows = a.stores().stream().map(this::toAnomalyStoreView).toList();
        return new AnomalyPromoView(p.promoId(), p.memoNumber(),
                a.stores().size() + " of " + p.storeCount(),
                p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE), p.durationText(),
                severityLabel(a.severity()), severityClass(a.severity()), rows);
    }

    private AnomalyStoreView toAnomalyStoreView(StoreAnomaly s) {
        StoreStatusRow row = s.store();
        return new AnomalyStoreView(row.storeId(), row.storeName(),
                row.enterprise().label(), statusClass(row.enterprise()),
                row.local().label(), statusClass(row.local()),
                severityLabel(s.severity()), severityClass(s.severity()));
    }

    // ---------- "Anomalies" tab, view "By Store" ----------

    private AnomalyStoreGroupView toGroupView(StoreAnomalyGroup g) {
        List<AnomalyPromoRowView> rows = g.promos().stream().map(x -> {
            StartingPromotion p = x.promo();
            StoreStatusRow row = x.anomaly().store();
            return new AnomalyPromoRowView(p.promoId(), p.memoNumber(),
                    p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE),
                    row.enterprise().label(), statusClass(row.enterprise()),
                    row.local().label(), statusClass(row.local()),
                    severityLabel(x.anomaly().severity()), severityClass(x.anomaly().severity()));
        }).toList();

        int n = g.promos().size();
        return new AnomalyStoreGroupView(g.storeId(), g.storeName(),
                n + (n == 1 ? " promo" : " promos"),
                severityLabel(g.severity()), severityClass(g.severity()), rows);
    }

    // ---------- helpers ----------

    private String statusClass(PromoStatus status) {
        return "status-" + status.name().toLowerCase().replace('_', '-');
    }

    private String severityLabel(Severity severity) {
        return severity == Severity.CRITICAL ? "Critical" : "Warning";
    }

    private String severityClass(Severity severity) {
        return "sev-" + severity.name().toLowerCase();
    }

    // ---------- shapes handed to the page ----------

    public record PromoView(int promoId, String memoNumber, int storeCount,
                            String startDate, String endDate, String duration,
                            List<StoreView> stores) {}

    public record StoreView(int storeId, String storeName,
                            String enterpriseLabel, String enterpriseClass,
                            String localLabel, String localClass) {}

    public record StoreGroupView(int storeId, String storeName, String promoCountText,
                                 List<StorePromoRowView> promos) {}

    public record StorePromoRowView(int promoId, String memoNumber,
                                    String startDate, String endDate,
                                    String enterpriseLabel, String enterpriseClass,
                                    String localLabel, String localClass) {}

    public record AnomalyPromoView(int promoId, String memoNumber, String storeCountText,
                                   String startDate, String endDate, String duration,
                                   String severityLabel, String severityClass,
                                   List<AnomalyStoreView> stores) {}

    public record AnomalyStoreView(int storeId, String storeName,
                                   String enterpriseLabel, String enterpriseClass,
                                   String localLabel, String localClass,
                                   String severityLabel, String severityClass) {}

    public record AnomalyStoreGroupView(int storeId, String storeName, String promoCountText,
                                        String severityLabel, String severityClass,
                                        List<AnomalyPromoRowView> promos) {}

    public record AnomalyPromoRowView(int promoId, String memoNumber,
                                      String startDate, String endDate,
                                      String enterpriseLabel, String enterpriseClass,
                                      String localLabel, String localClass,
                                      String severityLabel, String severityClass) {}
}