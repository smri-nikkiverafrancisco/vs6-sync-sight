package com.vs6.syncsight.promotions.ongoing.all.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotLoader;
import com.vs6.syncsight.promotions.shared.sync.AbstractPromotionSyncService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * All Ongoing Promotions (module 1.1.1): promotions where ActiveDateFrom <= today <= ActiveDateTo.
 * Everything else (background refresh, snapshot, checking, report) comes from the shared base.
 */
@Service
public class AllOngoingSyncService extends AbstractPromotionSyncService {

    private final AllOngoingPromotionService allOngoingPromotionService;

    public AllOngoingSyncService(
            AllOngoingPromotionService allOngoingPromotionService,
            PromotionSnapshotLoader snapshotLoader,
            PromotionChecker checker) {
        super(snapshotLoader, checker);
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