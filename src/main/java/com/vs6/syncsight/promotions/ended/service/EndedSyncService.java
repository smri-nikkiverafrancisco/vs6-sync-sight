package com.vs6.syncsight.promotions.ended.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.sync.AbstractPromotionSyncService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Ended Promotions (module 1.3), "Recent" part: promotions that ended in the last 30 days.
 * It only says which promotions to check; the refresh itself is run by PromotionRefreshCoordinator.
 */
@Service
public class EndedSyncService extends AbstractPromotionSyncService {

    private final EndedPromotionService endedPromotionService;

    public EndedSyncService(
            EndedPromotionService endedPromotionService,
            PromotionChecker checker) {
        super(checker);
        this.endedPromotionService = endedPromotionService;
    }

    @Override
    protected List<Promotion> loadPromotions() {
        return endedPromotionService.getRecentEnded();
    }

    @Override
    protected String description() {
        return "ended in the last 30 days";
    }
}