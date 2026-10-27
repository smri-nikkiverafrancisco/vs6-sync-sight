package com.vs6.syncsight.promotions.shared.sync;

import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotLoader;
import com.vs6.syncsight.promotions.shared.snapshot.RefreshProgress;
import com.vs6.syncsight.shared.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs ONE refresh for ALL Promotions submodules:
 *   1. read the stores and fetch Enterprise + Local into the album (once, for everybody)
 *   2. let every submodule check its own promotions against that same album
 *
 * Spring hands us every submodule automatically, so a new one (Upcoming, Ended, ...)
 * is included just by existing: nothing here needs to change.
 */
@Service
public class PromotionRefreshCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PromotionRefreshCoordinator.class);

    private final PromotionSnapshotLoader snapshotLoader;
    private final List<AbstractPromotionSyncService> submodules;

    // Progress board (read by the pages)
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final RefreshProgress progress = new RefreshProgress();
    private volatile String lastError;

    public PromotionRefreshCoordinator(PromotionSnapshotLoader snapshotLoader,
                                       List<AbstractPromotionSyncService> submodules) {
        this.snapshotLoader = snapshotLoader;
        this.submodules = submodules;
    }

    // BACKGROUND: "Start a refresh and come back to me right away."
    // Returns false if one is already running.
    public boolean startRefresh() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Thread worker = new Thread(() -> {
            try {
                refresh();
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("Refresh failed: {}", e.getMessage());
            } finally {
                progress.setStage("Idle");
                running.set(false);
            }
        }, "refresh-worker");
        worker.start();
        return true;
    }

    private void refresh() {
        lastError = null;
        progress.reset();

        // Steps 1-2: read the stores and fill the album, ONCE for everybody
        Map<Integer, Store> stores = snapshotLoader.loadAll(progress);
        Instant retrievedAt = Instant.now();

        // Steps 3-4: every submodule checks its own promotions against the same album.
        // If one submodule fails, the others still finish.
        List<String> problems = new ArrayList<>();
        for (AbstractPromotionSyncService submodule : submodules) {
            String name = submodule.getClass().getSimpleName();
            try {
                submodule.refreshFrom(stores, progress, retrievedAt);
            } catch (Exception e) {
                log.error("{} failed: {}", name, e.getMessage());
                problems.add(name + ": " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            lastError = String.join(" | ", problems);
        }
        log.info("Refresh done.");
    }

    // What the pages need to show progress
    public RefreshStatus getStatus() {
        return new RefreshStatus(running.get(), progress.getStage(),
                progress.getDone(), progress.getTotal(), lastError);
    }
}