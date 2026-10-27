package com.vs6.syncsight.promotions.ongoing.starting.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.sync.AbstractPromotionSyncService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Starting Promotions (module 1.1.2): promotions whose ActiveDateFrom is today.
 * It only says which promotions to check; the refresh itself is run by PromotionRefreshCoordinator.
 */
@Service
public class StartingPromotionSyncService extends AbstractPromotionSyncService {

    private final StartingPromotionService startingPromotionService;

    public StartingPromotionSyncService(
            StartingPromotionService startingPromotionService,
            PromotionChecker checker) {
        super(checker);
        this.startingPromotionService = startingPromotionService;
    }

    @Override
    protected List<Promotion> loadPromotions() {
        return startingPromotionService.getStartingToday();
    }

    @Override
    protected String description() {
        return "starting today";
    }
}