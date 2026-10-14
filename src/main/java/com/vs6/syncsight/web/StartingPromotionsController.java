package com.vs6.syncsight.web;

import com.vs6.syncsight.model.PromoStatus;
import com.vs6.syncsight.model.StartingPromotion;
import com.vs6.syncsight.model.StartingPromotionStatus;
import com.vs6.syncsight.model.StoreStatusRow;
import com.vs6.syncsight.service.PromotionSyncService;
import com.vs6.syncsight.service.PromotionSyncService.RefreshStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
public class StartingPromotionsController {

    private static final DateTimeFormatter HEADER_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);   // Wednesday, October 14, 2026
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PromotionSyncService syncService;

    public StartingPromotionsController(PromotionSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/starting-promotions";
    }

    @PostMapping("/starting-promotions/refresh")
    public String refresh() {
        syncService.startRefresh();
        return "redirect:/starting-promotions";
    }

    @GetMapping("/starting-promotions")
    public String page(Model model) {
        Instant refreshed = syncService.getLastRefreshed();
        RefreshStatus status = syncService.getStatus();
        List<PromoView> promos = syncService.getStartingReport().stream()
                .map(this::toView)
                .toList();

        String emptyMessage;
        if (refreshed == null) {
            emptyMessage = status.running()
                    ? "Loading the data... this page reloads by itself."
                    : "No data yet. Click Refresh.";
        } else {
            emptyMessage = "No promotions start today.";
        }

        String refreshText = status.stage()
                + (status.total() > 0 ? " (" + status.done() + "/" + status.total() + " stores)" : "");

        model.addAttribute("today", LocalDate.now().format(HEADER_DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? "--:--:--" : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promoCount", promos.size());
        model.addAttribute("promos", promos);
        model.addAttribute("emptyMessage", emptyMessage);
        model.addAttribute("refreshing", status.running());
        model.addAttribute("refreshText", refreshText);
        model.addAttribute("refreshError", status.lastError());
        return "starting-promotions";
    }

    private PromoView toView(StartingPromotionStatus status) {
        StartingPromotion p = status.promo();
        List<StoreView> stores = status.stores().stream().map(this::toStoreView).toList();
        return new PromoView(
                p.promoId(),
                p.memoNumber(),
                p.storeCount(),
                p.startDate().format(ROW_DATE),
                p.endDate().format(ROW_DATE),
                p.durationText(),
                stores);
    }

    private StoreView toStoreView(StoreStatusRow row) {
        return new StoreView(
                row.storeId(),
                row.storeName(),
                row.enterprise().label(),
                cssClass(row.enterprise()),
                row.local().label(),
                cssClass(row.local()));
    }

    // PromoStatus.API_ERROR becomes "status-api-error" (matches the CSS in the template)
    private String cssClass(PromoStatus status) {
        return "status-" + status.name().toLowerCase().replace('_', '-');
    }

    public record PromoView(int promoId, String memoNumber, int storeCount,
                            String startDate, String endDate, String duration,
                            List<StoreView> stores) {}

    public record StoreView(int storeId, String storeName,
                            String enterpriseLabel, String enterpriseClass,
                            String localLabel, String localClass) {}
}