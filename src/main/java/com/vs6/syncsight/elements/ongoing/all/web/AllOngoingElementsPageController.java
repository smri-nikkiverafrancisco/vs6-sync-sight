package com.vs6.syncsight.elements.ongoing.all.web;

import com.vs6.syncsight.elements.ongoing.all.service.AllOngoingElementsRefreshService;
import com.vs6.syncsight.elements.shared.web.AbstractElementsPageController;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Elements > Ongoing > All page: ongoing promos that started on the date the user picks. */
@Controller
@RequestMapping("/sync-sight/elements/ongoing/all")
public class AllOngoingElementsPageController extends AbstractElementsPageController {

    private static final String BASE_PATH = "/sync-sight/elements/ongoing/all";

    private final AllOngoingElementsRefreshService service;
    private final PromotionRefreshCoordinator promotionCoordinator;

    public AllOngoingElementsPageController(AllOngoingElementsRefreshService service,
                                            PromotionRefreshCoordinator promotionCoordinator) {
        super(service, promotionCoordinator,
                "All Ongoing Promotions Elements",
                BASE_PATH,
                "No ongoing promotions started on that date.");
        this.service = service;
        this.promotionCoordinator = promotionCoordinator;
    }

    // Added to the page for the date picker: the picked date, and today as the latest date allowed
    @ModelAttribute
    public void addDatePicker(Model model) {
        LocalDate picked = service.getStartDate();
        model.addAttribute("showDatePicker", true);
        model.addAttribute("pickedDate", picked == null ? "" : picked.toString());
        model.addAttribute("maxDate", LocalDate.now().toString());
    }

    // The Load button. Does nothing while a Promotions refresh is running.
    @PostMapping("/load")
    public String load(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(defaultValue = "all") String tab,
                       @RequestParam(defaultValue = "promo") String view) {
        if (!promotionCoordinator.getStatus().running()) {
            service.startRefresh(date);
        }
        String safeTab = "anomalies".equals(tab) ? "anomalies" : "all";
        String safeView = "store".equals(view) ? "store" : "promo";
        return "redirect:" + BASE_PATH + "?tab=" + safeTab + "&view=" + safeView;
    }

    @Override
    protected String noDataMessage() {
        return "Pick the date the promos started on, then click Load.";
    }
}