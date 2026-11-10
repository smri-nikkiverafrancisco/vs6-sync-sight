package com.vs6.syncsight.elements.upcoming.web;

import com.vs6.syncsight.elements.shared.web.AbstractElementsPageController;
import com.vs6.syncsight.elements.upcoming.service.UpcomingElementsRefreshService;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Elements > Upcoming page. */
@Controller
@RequestMapping("/sync-sight/elements/upcoming")
public class UpcomingElementsPageController extends AbstractElementsPageController {

    public UpcomingElementsPageController(UpcomingElementsRefreshService service,
                                          PromotionRefreshCoordinator promotionCoordinator) {
        super(service, promotionCoordinator,
                "Upcoming Promotions Elements",
                "/sync-sight/elements/upcoming",
                "No upcoming promotions.");
    }
}