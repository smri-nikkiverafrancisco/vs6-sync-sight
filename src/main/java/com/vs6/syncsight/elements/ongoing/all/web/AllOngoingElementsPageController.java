package com.vs6.syncsight.elements.ongoing.all.web;

import com.vs6.syncsight.elements.ongoing.all.service.AllOngoingElementsRefreshService;
import com.vs6.syncsight.elements.shared.web.AbstractElementsPageController;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Elements > Ongoing > All page. */
@Controller
@RequestMapping("/sync-sight/elements/ongoing/all")
public class AllOngoingElementsPageController extends AbstractElementsPageController {

    public AllOngoingElementsPageController(AllOngoingElementsRefreshService service,
                                            PromotionRefreshCoordinator promotionCoordinator) {
        super(service, promotionCoordinator,
                "All Ongoing Promotions Elements",
                "/sync-sight/elements/ongoing/all",
                "No promotions are ongoing today.");
    }
}