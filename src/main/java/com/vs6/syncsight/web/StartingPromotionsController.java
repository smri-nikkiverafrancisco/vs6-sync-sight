package com.vs6.syncsight.web;

import com.vs6.syncsight.model.*;
import com.vs6.syncsight.service.AnomalyDetector;
import com.vs6.syncsight.service.PromotionSyncService;
import com.vs6.syncsight.service.PromotionSyncService.RefreshStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
        return "redirect:/starting-promotions";
    }

    // The Refresh button: start the refresh, then come back to the same tab
    @PostMapping("/starting-promotions/refresh")
    public String refresh(@RequestParam(defaultValue = "all") String tab) {
        syncService.startRefresh();
        return "redirect:/starting-promotions?tab=" + tab;
    }

    @GetMapping("/starting-promotions")
    public String page(@RequestParam(defaultValue = "all") String tab, Model model) {
        boolean anomaliesTab = "anomalies".equals(tab);

        Instant refreshed = syncService.getLastRefreshed();
        RefreshStatus status = syncService.getStatus();

        List<StartingPromotionStatus> report = syncService.getStartingReport();
        List<PromoView> promos = report.stream().map(this::toView).toList();

        List<PromoAnomaly> anomalies = anomalyDetector.detect(report);
        List<AnomalyPromoView> anomalyViews = anomalies.stream().map(this::toAnomalyView).toList();
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
        model.addAttribute("today", LocalDate.now().format(HEADER_DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? "--:--:--" : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promoCount", promos.size());
        model.addAttribute("promos", promos);
        model.addAttribute("anomalyPromos", anomalyViews);
        model.addAttribute("criticalCount", criticalCount);
        model.addAttribute("warningCount", warningCount);
        model.addAttribute("nothingToShow", anomaliesTab ? anomalyViews.isEmpty() : promos.isEmpty());
        model.addAttribute("emptyMessage", emptyMessage);
        model.addAttribute("refreshing", status.running());
        model.addAttribute("refreshText", refreshText);
        model.addAttribute("refreshError", status.lastError());
        return "starting-promotions";
    }

    // ---------- "All" tab ----------

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

    // ---------- "Anomalies" tab ----------

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
                severityLabel(s.severity()), severityClass(s.severity()), s.reasonText());
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

    public record PromoView(int promoId, String memoNumber, int storeCount,
                            String startDate, String endDate, String duration,
                            List<StoreView> stores) {}

    public record StoreView(int storeId, String storeName,
                            String enterpriseLabel, String enterpriseClass,
                            String localLabel, String localClass) {}

    public record AnomalyPromoView(int promoId, String memoNumber, String storeCountText,
                                   String startDate, String endDate, String duration,
                                   String severityLabel, String severityClass,
                                   List<AnomalyStoreView> stores) {}

    public record AnomalyStoreView(int storeId, String storeName,
                                   String enterpriseLabel, String enterpriseClass,
                                   String localLabel, String localClass,
                                   String severityLabel, String severityClass,
                                   String reason) {}
}