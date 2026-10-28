package com.vs6.syncsight.promotions.upcoming.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.sync.AbstractPromotionSyncService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Upcoming Promotions (module 1.2): promotions where ActiveDateFrom is after today.
 * It only says which promotions to check; the refresh itself is run by PromotionRefreshCoordinator.
 */
@Service
public class UpcomingSyncService extends AbstractPromotionSyncService {

    private final UpcomingPromotionService upcomingPromotionService;

    public UpcomingSyncService(
            UpcomingPromotionService upcomingPromotionService,
            PromotionChecker checker) {
        super(checker);
        this.upcomingPromotionService = upcomingPromotionService;
    }

    @Override
    protected List<Promotion> loadPromotions() {
        return upcomingPromotionService.getUpcoming();
    }

    @Override
    protected String description() {
        return "starting after today";
    }
}