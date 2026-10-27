package com.vs6.syncsight.promotions.ongoing.all.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.sync.AbstractPromotionSyncService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * All Ongoing Promotions (module 1.1.1): promotions where ActiveDateFrom <= today <= ActiveDateTo.
 * It only says which promotions to check; the refresh itself is run by PromotionRefreshCoordinator.
 */
@Service
public class AllOngoingSyncService extends AbstractPromotionSyncService {

    private final AllOngoingPromotionService allOngoingPromotionService;

    public AllOngoingSyncService(
            AllOngoingPromotionService allOngoingPromotionService,
            PromotionChecker checker) {
        super(checker);
        this.allOngoingPromotionService = allOngoingPromotionService;
    }

    @Override
    protected List<Promotion> loadPromotions() {
        return allOngoingPromotionService.getAllOngoing();
    }

    @Override
    protected String description() {
        return "ongoing today";
    }
}