package com.vs6.syncsight.elements.ongoing.starting.web;

import com.vs6.syncsight.elements.ongoing.starting.service.StartingElementsRefreshService;
import com.vs6.syncsight.elements.shared.web.AbstractElementsPageController;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Elements > Ongoing > Starting page.
 * All the page logic is in AbstractElementsPageController; this class only gives it
 * the refresh service, the page title, the URL path and the empty-page message.
 */
@Controller
@RequestMapping("/sync-sight/elements/ongoing/starting")
public class StartingElementsPageController extends AbstractElementsPageController {

    public StartingElementsPageController(StartingElementsRefreshService service,
                                          PromotionRefreshCoordinator promotionCoordinator) {
        super(service, promotionCoordinator,
                "Starting Promotions Elements",
                "/sync-sight/elements/ongoing/starting",
                "No promotions start today.");
    }
}