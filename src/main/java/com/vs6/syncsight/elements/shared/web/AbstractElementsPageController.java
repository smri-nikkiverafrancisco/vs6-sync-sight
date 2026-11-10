package com.vs6.syncsight.elements.shared.web;

import com.vs6.syncsight.elements.shared.model.ElementCount;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.model.ElementPromotionCheck;
import com.vs6.syncsight.elements.shared.model.ElementStatus;
import com.vs6.syncsight.elements.shared.model.ElementStatusEvaluator;
import com.vs6.syncsight.elements.shared.model.ElementStoreRow;
import com.vs6.syncsight.elements.shared.sync.AbstractElementsRefreshService;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import com.vs6.syncsight.promotions.shared.sync.RefreshStatus;
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
import java.util.function.Predicate;

/**
 * The page logic that is the same for every Elements page (Starting, All Ongoing, ...).
 * A page's controller only gives it: its refresh service, its page title, its URL path
 * and the message to show when there are no promos.
 * The URL path itself is set on the page's controller with @RequestMapping.
 * Tabs: "All" (every promo and store) and "Anomalies" (one section per kind of problem).
 */
public abstract class AbstractElementsPageController {

    private static final DateTimeFormatter HEADER_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // The sections of the Anomalies tab, in this order
    private static final List<ElementStatus> SECTION_ORDER = List.of(
            ElementStatus.NO_ELEMENTS,
            ElementStatus.NOT_TALLY,
            ElementStatus.MISMATCH,
            ElementStatus.SOURCE_MISMATCH,
            ElementStatus.INCOMPLETE);          // shown as "API Error"

    private final AbstractElementsRefreshService service;
    private final PromotionRefreshCoordinator promotionCoordinator;
    private final String pageTitle;
    private final String basePath;
    private final String emptyPromosMessage;

    protected AbstractElementsPageController(AbstractElementsRefreshService service,
                                             PromotionRefreshCoordinator promotionCoordinator,
                                             String pageTitle,
                                             String basePath,
                                             String emptyPromosMessage) {
        this.service = service;
        this.promotionCoordinator = promotionCoordinator;
        this.pageTitle = pageTitle;
        this.basePath = basePath;
        this.emptyPromosMessage = emptyPromosMessage;
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
        return "redirect:" + basePath + "?tab=" + safeTab + "&view=" + safeView;
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

        // "All" tab: every row.  "Anomalies" tab: one section per kind of problem.
        List<PromoView> promos = anomaliesTab ? List.of() : toPromoViews(report, s -> true, false);
        List<StoreGroupView> storeGroups = anomaliesTab ? List.of() : toStoreGroups(report, s -> true);
        List<SectionView> sections = toSections(report);
        long anomalyCount = sections.stream().mapToLong(SectionView::rowCount).sum();
        boolean nothingToShow = anomaliesTab ? sections.isEmpty() : promos.isEmpty();

        String emptyMessage;
        if (refreshed == null) {
            emptyMessage = status.running()
                    ? "Loading the data... this page reloads by itself."
                    : noDataMessage();
        } else {
            emptyMessage = anomaliesTab ? "No anomalies found." : emptyPromosMessage;
        }

        String refreshText = status.stage()
                + (status.total() > 0 ? " (" + status.done() + "/" + status.total() + " stores)" : "");

        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("basePath", basePath);
        model.addAttribute("tab", anomaliesTab ? "anomalies" : "all");
        model.addAttribute("view", "store".equals(view) ? "store" : "promo");
        model.addAttribute("today", LocalDate.now().format(HEADER_DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? "--:--:--" : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promoCount", report.size());
        model.addAttribute("anomalyCount", anomalyCount);
        model.addAttribute("promos", promos);
        model.addAttribute("storeGroups", storeGroups);
        model.addAttribute("sections", sections);
        model.addAttribute("nothingToShow", nothingToShow);
        model.addAttribute("emptyMessage", emptyMessage);
        model.addAttribute("refreshing", status.running());
        model.addAttribute("promotionsRefreshing", promotionCoordinator.getStatus().running());
        model.addAttribute("refreshText", refreshText);
        model.addAttribute("refreshError", status.lastError());
        return "elements/shared/element-status";
    }

    // ---------- Anomalies tab: the sections ----------

    private List<SectionView> toSections(List<ElementPromotionCheck> report) {
        List<SectionView> sections = new ArrayList<>();
        for (ElementStatus status : SECTION_ORDER) {
            Predicate<ElementStatus> onlyThisStatus = s -> s == status;
            List<PromoView> promos = toPromoViews(report, onlyThisStatus, true);
            if (promos.isEmpty()) {
                continue;                                   // empty sections are hidden
            }
            long rows = promos.stream().mapToLong(p -> p.stores().size()).sum();
            sections.add(new SectionView(status.label(), statusClass(status), rows,
                    promos, toStoreGroups(report, onlyThisStatus)));
        }
        return sections;
    }

    // ---------- By Promo ----------

    // 'include' decides which store rows are kept.
    // partial = true: promos with no kept rows are left out, and the store count reads "2 of 5".
    private List<PromoView> toPromoViews(List<ElementPromotionCheck> report,
                                         Predicate<ElementStatus> include, boolean partial) {
        List<PromoView> views = new ArrayList<>();
        for (ElementPromotionCheck check : report) {
            ElementPromotion p = check.promo();
            List<StoreView> stores = new ArrayList<>();
            for (ElementStoreRow row : check.stores()) {
                ElementStatus st = statusOf(p, row);
                if (!include.test(st)) {
                    continue;
                }
                stores.add(toStoreView(row, st));
            }
            if (partial && stores.isEmpty()) {
                continue;
            }
            String storeCountText = partial
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
    private List<StoreGroupView> toStoreGroups(List<ElementPromotionCheck> report,
                                               Predicate<ElementStatus> include) {
        Map<Integer, List<StorePromoRowView>> byStore = new TreeMap<>();   // sorted by store ID
        Map<Integer, String> names = new HashMap<>();

        for (ElementPromotionCheck check : report) {
            ElementPromotion p = check.promo();
            for (ElementStoreRow row : check.stores()) {
                ElementStatus st = statusOf(p, row);
                if (!include.test(st)) {
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

    // The message shown before any data has been loaded. A page can change it.
    protected String noDataMessage() {
        return "No data yet.";
    }

    // ---------- helpers ----------

    private ElementStatus statusOf(ElementPromotion p, ElementStoreRow row) {
        return ElementStatusEvaluator.evaluate(row.storeId(), p.elementCount(), row.enterprise(), row.local());
    }

    // A number when the count was fetched, otherwise the reason it was not
    private String countText(ElementCount count) {
        if (count.isOk()) {
            return Long.toString(count.total());
        }
        return switch (count.status()) {
            case API_ERROR -> "API Error";
            case NO_ELEMENTS -> "No Elements";
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

    public record SectionView(String title, String titleClass, long rowCount,
                              List<PromoView> promos, List<StoreGroupView> storeGroups) {}

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