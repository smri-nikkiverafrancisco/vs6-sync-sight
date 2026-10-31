package com.vs6.syncsight.promotions.ended.web;

import com.vs6.syncsight.promotions.ended.anomaly.EndedAnomalyDetector;
import com.vs6.syncsight.promotions.ended.service.EndedSyncService;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import com.vs6.syncsight.promotions.shared.web.PromotionStatusPageController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Ended Promotions page (module 1.3).
 * All the page logic is in PromotionStatusPageController; this class only gives it
 * the refresh coordinator, the Ended report, its OWN anomaly rules, the page title,
 * the URL path and the empty-page message.
 */
@Controller
@RequestMapping("/sync-sight/promotions/ended")
public class EndedPromotionsController extends PromotionStatusPageController {

    public EndedPromotionsController(PromotionRefreshCoordinator coordinator,
                                     EndedSyncService syncService,
                                     EndedAnomalyDetector anomalyDetector) {
        super(coordinator, syncService, anomalyDetector,
                "Ended Promotions",
                "/sync-sight/promotions/ended",
                "No promotions ended in the last 30 days.");
    }

    // The first tab lists the recently ended promotions, so it is called "Recent" instead of "All"
    @Override
    protected String allTabLabel() {
        return "Recent";
    }
}