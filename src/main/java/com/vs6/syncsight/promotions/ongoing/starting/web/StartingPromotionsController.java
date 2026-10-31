package com.vs6.syncsight.promotions.ongoing.starting.web;

import com.vs6.syncsight.promotions.ongoing.starting.service.StartingPromotionSyncService;
import com.vs6.syncsight.promotions.shared.anomaly.AnomalyDetector;
import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import com.vs6.syncsight.promotions.shared.web.PromotionStatusPageController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Starting Promotions page (module 1.1.2).
 * All the page logic is in PromotionStatusPageController; this class only gives it
 * the refresh coordinator, the Starting report, the page title, the URL path and the empty-page message.
 */
@Controller
@RequestMapping("/sync-sight/promotions/ongoing/starting")
public class StartingPromotionsController extends PromotionStatusPageController {

    public StartingPromotionsController(PromotionRefreshCoordinator coordinator,
                                        StartingPromotionSyncService syncService,
                                        AnomalyDetector anomalyDetector) {
        super(coordinator, syncService, anomalyDetector,
                "Starting Promotions",
                "/sync-sight/promotions/ongoing/starting",
                "No promotions start today.");
    }
}