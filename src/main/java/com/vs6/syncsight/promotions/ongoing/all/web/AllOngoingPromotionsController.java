package com.vs6.syncsight.promotions.ongoing.all.web;

import com.vs6.syncsight.promotions.ongoing.all.service.AllOngoingSyncService;
import com.vs6.syncsight.promotions.shared.anomaly.AnomalyDetector;
import com.vs6.syncsight.promotions.shared.web.PromotionStatusPageController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * All Ongoing Promotions page (module 1.1.1).
 * All the page logic is in PromotionStatusPageController; this class only gives it
 * the All Ongoing sync service, the page title, the URL path and the empty-page message.
 */
@Controller
@RequestMapping("/promotions/ongoing/all")
public class AllOngoingPromotionsController extends PromotionStatusPageController {

    public AllOngoingPromotionsController(AllOngoingSyncService syncService,
                                          AnomalyDetector anomalyDetector) {
        super(syncService, anomalyDetector,
                "All Ongoing Promotions",
                "/promotions/ongoing/all",
                "No ongoing promotions today.");
    }
}