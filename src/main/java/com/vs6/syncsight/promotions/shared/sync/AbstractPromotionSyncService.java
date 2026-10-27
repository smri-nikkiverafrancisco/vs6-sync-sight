package com.vs6.syncsight.promotions.shared.sync;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.model.PromotionCheck;
import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotLoader;
import com.vs6.syncsight.promotions.shared.snapshot.RefreshProgress;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import com.vs6.syncsight.shared.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The refresh logic that is the same for every Promotions submodule
 * (Starting, All Ongoing, Upcoming, Ended):
 * run in the background, load the snapshot, check the promotions, keep the report for the page.
 *
 * A submodule only says WHICH promotions to check (its own SQL) by extending this class
 * and filling in loadPromotions() and description().
 */
public abstract class AbstractPromotionSyncService {

    private static final Logger log = LoggerFactory.getLogger(AbstractPromotionSyncService.class);

    private final PromotionSnapshotLoader snapshotLoader;
    private final PromotionChecker checker;

    // Progress board (read by the page)
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final RefreshProgress progress = new RefreshProgress();
    private volatile String lastError;

    // What the page shows
    private volatile List<PromotionCheck> report = List.of();
    private volatile Instant lastRefreshed;

    protected AbstractPromotionSyncService(PromotionSnapshotLoader snapshotLoader, PromotionChecker checker) {
        this.snapshotLoader = snapshotLoader;
        this.checker = checker;
    }

    // THE ONLY THING A SUBMODULE MUST PROVIDE: which promotions to check (its own SQL)
    protected abstract List<Promotion> loadPromotions();

    // Short wording for the log lines, for example "starting today" or "ongoing today"
    protected abstract String description();

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

        // Steps 1-2 (shared): read the stores and fetch Enterprise + Local into the album
        Map<Integer, Store> stores = snapshotLoader.loadAll(progress);

        // 3. This submodule's promotions, from the database
        progress.setStage("Reading promotions");
        log.info("Step 3: Reading promotions {}...", description());
        List<Promotion> promotions = loadPromotions();
        log.info("{} promos {}", promotions.size(), description());

        // 4. Check each promo's status in the album
        progress.setStage("Checking statuses");
        log.info("Step 4: Checking promotions {}...", description());
        List<PromotionCheck> checked = checker.check(promotions, stores);

        Map<PromoStatus, Long> counts = checked.stream()
                .flatMap(r -> r.stores().stream())
                .flatMap(s -> Stream.of(s.enterprise(), s.local()))
                .collect(Collectors.groupingBy(s -> s,
                        () -> new EnumMap<>(PromoStatus.class), Collectors.counting()));
        log.info("Status counts (Enterprise + Local): {}", counts);

        // Swap in the new results all at once, so the page never sees a half-finished report
        this.report = checked;
        this.lastRefreshed = Instant.now();
        log.info("Refresh done.");
    }

    public RefreshStatus getStatus() {
        return new RefreshStatus(running.get(), progress.getStage(),
                progress.getDone(), progress.getTotal(), lastError);
    }

    public List<PromotionCheck> getReport() { return report; }

    public Instant getLastRefreshed() { return lastRefreshed; }
}