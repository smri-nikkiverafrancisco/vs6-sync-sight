package com.vs6.syncsight.promotions.upcoming.web;

import com.vs6.syncsight.promotions.shared.sync.PromotionRefreshCoordinator;
import com.vs6.syncsight.promotions.shared.web.PromotionStatusPageController;
import com.vs6.syncsight.promotions.upcoming.anomaly.UpcomingAnomalyDetector;
import com.vs6.syncsight.promotions.upcoming.service.UpcomingSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Upcoming Promotions page (module 1.2).
 * All the page logic is in PromotionStatusPageController; this class only gives it
 * the refresh coordinator, the Upcoming report, its OWN anomaly rules, the page title,
 * the URL path and the empty-page message.
 */
@Controller
@RequestMapping("/promotions/upcoming")
public class UpcomingPromotionsController extends PromotionStatusPageController {

    public UpcomingPromotionsController(PromotionRefreshCoordinator coordinator,
                                        UpcomingSyncService syncService,
                                        UpcomingAnomalyDetector anomalyDetector) {
        super(coordinator, syncService, anomalyDetector,
                "Upcoming Promotions",
                "/promotions/upcoming",
                "No upcoming promotions.");
    }
}