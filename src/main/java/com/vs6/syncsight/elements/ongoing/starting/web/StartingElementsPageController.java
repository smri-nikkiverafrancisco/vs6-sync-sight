package com.vs6.syncsight.elements.ongoing.starting.web;

import com.vs6.syncsight.elements.ongoing.starting.service.StartingElementsRefreshService;
import com.vs6.syncsight.elements.shared.model.ElementCount;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.model.ElementPromotionCheck;
import com.vs6.syncsight.elements.shared.model.ElementStatus;
import com.vs6.syncsight.elements.shared.model.ElementStatusEvaluator;
import com.vs6.syncsight.elements.shared.model.ElementStoreRow;
import com.vs6.syncsight.promotions.shared.sync.RefreshStatus;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

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
    @PostMapping("/refresh")
    public String refresh(@RequestParam(defaultValue = "promo") String view) {
        if (!promotionCoordinator.getStatus().running()) {
            service.startRefresh();
        }
        return "redirect:" + BASE_PATH + "?view=" + ("store".equals(view) ? "store" : "promo");
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "promo") String view, Model model) {
        service.startIfNeverStarted();

        Instant refreshed = service.getLastRefreshed();
        RefreshStatus status = service.getStatus();
        List<ElementPromotionCheck> report = service.getReport();

        List<PromoView> promos = report.stream().map(this::toPromoView).toList();
        List<StoreGroupView> storeGroups = toStoreGroups(report);

        String emptyMessage;
        if (refreshed == null) {
            emptyMessage = status.running()
                    ? "Loading the data... this page reloads by itself."
                    : "No data yet.";
        } else {
            emptyMessage = "No promotions start today.";
        }

        String refreshText = status.stage()
                + (status.total() > 0 ? " (" + status.done() + "/" + status.total() + " stores)" : "");

        model.addAttribute("pageTitle", "Starting Promotions Elements");
        model.addAttribute("basePath", BASE_PATH);
        model.addAttribute("view", "store".equals(view) ? "store" : "promo");
        model.addAttribute("today", LocalDate.now().format(HEADER_DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? "--:--:--" : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promoCount", promos.size());
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

    private PromoView toPromoView(ElementPromotionCheck check) {
        ElementPromotion p = check.promo();
        List<StoreView> stores = check.stores().stream().map(row -> toStoreView(p, row)).toList();
        return new PromoView(p.promoId(), p.memoNumber(), p.elementCount(), p.storeCount(),
                p.startDate().format(ROW_DATE), p.endDate().format(ROW_DATE), p.durationText(), stores);
    }

    private StoreView toStoreView(ElementPromotion p, ElementStoreRow row) {
        ElementStatus st = ElementStatusEvaluator.evaluate(
                row.storeId(), p.elementCount(), row.enterprise(), row.local());
        return new StoreView(row.storeId(), row.storeName(),
                countText(row.enterprise()), countClass(row.enterprise(), st.enterpriseColor()),
                countText(row.local()), countClass(row.local(), st.localColor()),
                st.label(), statusClass(st));
    }

    // ---------- By Store ----------

    // Flip the report: from "promos with their stores" to "stores with their promos"
    private List<StoreGroupView> toStoreGroups(List<ElementPromotionCheck> report) {
        Map<Integer, List<StorePromoRowView>> byStore = new TreeMap<>();   // sorted by store ID
        Map<Integer, String> names = new HashMap<>();

        for (ElementPromotionCheck check : report) {
            ElementPromotion p = check.promo();
            for (ElementStoreRow row : check.stores()) {
                ElementStatus st = ElementStatusEvaluator.evaluate(
                        row.storeId(), p.elementCount(), row.enterprise(), row.local());
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

    public record PromoView(int promoId, String memoNumber, long elementCount, int storeCount,
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