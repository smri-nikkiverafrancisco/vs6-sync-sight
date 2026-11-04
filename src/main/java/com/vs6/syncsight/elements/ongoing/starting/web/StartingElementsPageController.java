package com.vs6.syncsight.elements.ongoing.starting.web;

import com.vs6.syncsight.elements.ongoing.starting.service.StartingElementsRefreshService;
import com.vs6.syncsight.elements.shared.model.ElementCount;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.model.ElementPromotionCheck;
import com.vs6.syncsight.elements.shared.model.ElementStatus;
import com.vs6.syncsight.elements.shared.model.ElementStatusEvaluator;
import com.vs6.syncsight.elements.shared.model.ElementStoreRow;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import com.vs6.syncsight.promotions.shared.sync.RefreshStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

/**
 * Elements > Ongoing > Starting page.
 * Opening it starts the Elements load (only the first time), then shows the report.
 * Tabs: "All" (every promo and store) and "Anomalies" (only the rows with a problem).
 */
@Controller
@RequestMapping("/sync-sight/elements/ongoing/starting")
public class StartingElementsPageController {

    private static final String BASE_PATH = "/sync-sight/elements/ongoing/starting";
    private static final DateTimeFormatter HEADER_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StartingElementsRefreshService service;
    private final PromotionRefreshCoordinator promotionCoordinator;

    public StartingElementsPageController(StartingElementsRefreshService service,
                                          PromotionRefreshCoordinator promotionCoordinator) {
        this.service = service;
        this.promotionCoordinator = promotionCoordinator;
    }

    // The Refresh button. Does nothing while a Promotions refresh is running.
    // Comes back to the same tab and view.
    @PostMapping("/refresh")
    public String refresh(@RequestParam(defaultValue = "all") String tab,
                          @RequestParam(defaultValue = "promo") String view) {
        if (!promotionCoordinator.getStatus().running()) {
            service.startRefresh();
        }
        String safeTab = "anomalies".equals(tab) ? "anomalies" : "all";
        String safeView = "store".equals(view) ? "store" : "promo";
        return "redirect:" + BASE_PATH + "?tab=" + safeTab + "&view=" + safeView;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "all") String tab,
                       @RequestParam(defaultValue = "promo") String view,
                       Model model) {
        service.startIfNeverStarted();
        boolean anomaliesTab = "anomalies".equals(tab);

        Instant refreshed = service.getLastRefreshed();
        RefreshStatus status = service.getStatus();
        List<ElementPromotionCheck> report = service.getReport();

        // The current tab decides which rows are shown
        List<PromoView> promos = toPromoViews(report, anomaliesTab);
        List<StoreGroupView> storeGroups = toStoreGroups(report, anomaliesTab);
        long anomalyCount = countAnomalies(report);

        String emptyMessage;
        if (refreshed == null) {
            emptyMessage = status.running()
                    ? "Loading the data... this page reloads by itself."
                    : "No data yet.";
        } else {
            emptyMessage = anomaliesTab ? "No anomalies found." : "No promotions start today.";
        }

        String refreshText = status.stage()
                + (status.total() > 0 ? " (" + status.done() + "/" + status.total() + " stores)" : "");

        model.addAttribute("pageTitle", "Starting Promotions Elements");
        model.addAttribute("basePath", BASE_PATH);
        model.addAttribute("tab", anomaliesTab ? "anomalies" : "all");
        model.addAttribute("view", "store".equals(view) ? "store" : "promo");
        model.addAttribute("today", LocalDate.now().format(HEADER_DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? "--:--:--" : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promoCount", report.size());
        model.addAttribute("anomalyCount", anomalyCount);
        model.addAttribute("promos", promos);
        model.addAttribute("storeGroups", storeGroups);
        model.addAttribute("nothingToShow", promos.isEmpty());
        model.addAttribute("emptyMessage", emptyMessage);
        model.addAttribute("refreshing", status.running());
        model.addAttribute("promotionsRefreshing", promotionCoordinator.getStatus().running());
        model.addAttribute("refreshText", refreshText);
        model.addAttribute("refreshError", status.lastError());
        return "elements/shared/element-status";
    }

    // ---------- By Promo ----------

    // anomaliesOnly = false: every promo with all its stores
    // anomaliesOnly = true:  only promos that have at least one store with a problem, listing only those stores
    private List<PromoView> toPromoViews(List<ElementPromotionCheck> report, boolean anomaliesOnly) {
        List<PromoView> views = new ArrayList<>();
        for (ElementPromotionCheck check : report) {
            ElementPromotion p = check.promo();
            List<StoreView> stores = new ArrayList<>();
            for (ElementStoreRow row : check.stores()) {
                ElementStatus st = statusOf(p, row);
                if (anomaliesOnly && !st.isAnomaly()) {
                    continue;
                }
                stores.add(toStoreView(row, st));
            }
            if (anomaliesOnly && stores.isEmpty()) {
                continue;
            }
            String storeCountText = anomaliesOnly
                    ? stores.size() + " of " + p.storeCount()
                    : String.valueOf(p.storeCount());
            views.add(new PromoView(p.promoId(), p.memoNumber(), p.elementCount(), storeCountText,
                    p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE), p.durationText(), stores));
        }
        return views;
    }

    private StoreView toStoreView(ElementStoreRow row, ElementStatus st) {
        return new StoreView(row.storeId(), row.storeName(),
                countText(row.enterprise()), countClass(row.enterprise(), st.enterpriseColor()),
                countText(row.local()), countClass(row.local(), st.localColor()),
                st.label(), statusClass(st));
    }

    // ---------- By Store ----------

    // Flip the report: from "promos with their stores" to "stores with their promos"
    private List<StoreGroupView> toStoreGroups(List<ElementPromotionCheck> report, boolean anomaliesOnly) {
        Map<Integer, List<StorePromoRowView>> byStore = new TreeMap<>();   // sorted by store ID
        Map<Integer, String> names = new HashMap<>();

        for (ElementPromotionCheck check : report) {
            ElementPromotion p = check.promo();
            for (ElementStoreRow row : check.stores()) {
                ElementStatus st = statusOf(p, row);
                if (anomaliesOnly && !st.isAnomaly()) {
                    continue;
                }
                names.putIfAbsent(row.storeId(), row.storeName());
                byStore.computeIfAbsent(row.storeId(), k -> new ArrayList<>())
                        .add(new StorePromoRowView(p.promoId(), p.memoNumber(), p.elementCount(),
                                p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE),
                                countText(row.enterprise()), countClass(row.enterprise(), st.enterpriseColor()),
                                countText(row.local()), countClass(row.local(), st.localColor()),
                                st.label(), statusClass(st)));
            }
        }

        List<StoreGroupView> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<StorePromoRowView>> e : byStore.entrySet()) {
            List<StorePromoRowView> rows = e.getValue();
            rows.sort(Comparator.comparingInt(StorePromoRowView::promoId));
            int n = rows.size();
            groups.add(new StoreGroupView(e.getKey(), names.get(e.getKey()),
                    n + (n == 1 ? " promo" : " promos"), rows));
        }
        return groups;
    }

    // ---------- helpers ----------

    private ElementStatus statusOf(ElementPromotion p, ElementStoreRow row) {
        return ElementStatusEvaluator.evaluate(row.storeId(), p.elementCount(), row.enterprise(), row.local());
    }

    // How many promo-store rows have a problem (the number on the Anomalies tab)
    private long countAnomalies(List<ElementPromotionCheck> report) {
        return report.stream()
                .flatMap(check -> check.stores().stream().map(row -> statusOf(check.promo(), row)))
                .filter(ElementStatus::isAnomaly)
                .count();
    }

    // A number when the count was fetched, otherwise the reason it was not
    private String countText(ElementCount count) {
        if (count.isOk()) {
            return Long.toString(count.total());
        }
        return switch (count.status()) {
            case API_ERROR -> "API Error";
            case CONNECTION_ERROR -> "Connection Error";
            case TIMEOUT -> "Timeout";
            case NO_LOCAL_SITE -> "No Local Site";
            case OK -> "";
            case NO_ELEMENTS -> "No Elements";
        };
    }

    // Error labels keep their own colour; a fetched count gets green or amber when the status says so
    private String countClass(ElementCount count, String color) {
        if (!count.isOk()) {
            return "badge status-" + count.status().name().toLowerCase().replace('_', '-');
        }
        return color.isEmpty() ? "num" : "badge hl-" + color;
    }

    private String statusClass(ElementStatus status) {
        return status.label().isEmpty()
                ? ""
                : "badge st-" + status.name().toLowerCase().replace('_', '-');
    }

    // ---------- shapes handed to the page ----------

    public record PromoView(int promoId, String memoNumber, long elementCount, String storeCountText,
                            String startDate, String endDate, String duration, List<StoreView> stores) {}

    public record StoreView(int storeId, String storeName,
                            String enterpriseText, String enterpriseClass,
                            String localText, String localClass,
                            String statusText, String statusClass) {}

    public record StoreGroupView(int storeId, String storeName, String promoCountText,
                                 List<StorePromoRowView> promos) {}

    public record StorePromoRowView(int promoId, String memoNumber, long elementCount,
                                    String startDate, String endDate,
                                    String enterpriseText, String enterpriseClass,
                                    String localText, String localClass,
                                    String statusText, String statusClass) {}
}