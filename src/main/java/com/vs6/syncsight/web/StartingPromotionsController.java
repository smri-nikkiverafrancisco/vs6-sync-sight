package com.vs6.syncsight.web;

import com.vs6.syncsight.model.PromoStatus;
import com.vs6.syncsight.model.StartingPromotion;
import com.vs6.syncsight.model.StartingPromotionStatus;
import com.vs6.syncsight.model.StoreStatusRow;
import com.vs6.syncsight.service.PromotionSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class StartingPromotionsController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PromotionSyncService syncService;

    public StartingPromotionsController(PromotionSyncService syncService) {
        this.syncService = syncService;
    }

    // Opening http://localhost:8080 goes to the page
    @GetMapping("/")
    public String home() {
        return "redirect:/starting-promotions";
    }

    @GetMapping("/starting-promotions")
    public String page(Model model) {
        Instant refreshed = syncService.getLastRefreshed();
        List<PromoView> promos = syncService.getStartingReport().stream()
                .map(this::toView)
                .toList();

        model.addAttribute("today", LocalDate.now().format(DATE));
        model.addAttribute("retrievedTime",
                refreshed == null ? null : TIME.format(refreshed.atZone(ZoneId.systemDefault())));
        model.addAttribute("promos", promos);
        return "starting-promotions";      // = templates/starting-promotions.html
    }

    // Turn the report into text that is ready to show
    private PromoView toView(StartingPromotionStatus status) {
        StartingPromotion p = status.promo();
        List<StoreView> stores = status.stores().stream().map(this::toStoreView).toList();
        return new PromoView(
                p.promoId(),
                p.memoNumber(),
                p.storeCount(),
                p.startDate().format(DATE),
                p.endDate().format(DATE),
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

    // PromoStatus.API_ERROR becomes "st-api-error" (used for the badge color)
    private String cssClass(PromoStatus status) {
        return "st-" + status.name().toLowerCase().replace('_', '-');
    }

    // The "ready to show" shapes
    public record PromoView(int promoId, String memoNumber, int storeCount,
                            String startDate, String endDate, String duration,
                            List<StoreView> stores) {}

    public record StoreView(int storeId, String storeName,
                            String enterprise, String enterpriseClass,
                            String local, String localClass) {}
}