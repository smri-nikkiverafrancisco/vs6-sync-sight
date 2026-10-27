package com.vs6.syncsight.promotions.ongoing.starting.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotLoader;
import com.vs6.syncsight.promotions.shared.sync.AbstractPromotionSyncService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Starting Promotions (module 1.1.2): promotions whose ActiveDateFrom is today.
 * Everything else (background refresh, snapshot, checking, report) comes from the shared base.
 */
@Service
public class StartingPromotionSyncService extends AbstractPromotionSyncService {

    private final StartingPromotionService startingPromotionService;

    public StartingPromotionSyncService(
            StartingPromotionService startingPromotionService,
            PromotionSnapshotLoader snapshotLoader,
            PromotionChecker checker) {
        super(snapshotLoader, checker);
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