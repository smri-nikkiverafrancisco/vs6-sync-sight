package com.vs6.syncsight.promotions.ongoing.starting.service;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.model.PromotionCheck;
import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotLoader;
import com.vs6.syncsight.promotions.shared.snapshot.RefreshProgress;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import com.vs6.syncsight.shared.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Starting Promotions half of a refresh.
 * The shared work (reading stores, fetching Enterprise and Local) is done by PromotionSnapshotLoader;
 * this class adds what is only for Starting: today's starting promos, the status check,
 * and the report the page shows.
 */
@Service
public class PromotionSyncService {

    private static final Logger log = LoggerFactory.getLogger(PromotionSyncService.class);

    private final StartingPromotionService startingPromotionService;
    private final PromotionSnapshotLoader snapshotLoader;
    private final PromotionChecker checker;

    // Progress board (read by the page)
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final RefreshProgress progress = new RefreshProgress();
    private volatile String lastError;

    // What the page shows
    private volatile List<PromotionCheck> startingReport = List.of();
    private volatile Instant lastRefreshed;

    public PromotionSyncService(
            StartingPromotionService startingPromotionService,
            PromotionSnapshotLoader snapshotLoader,
            PromotionChecker checker) {
        this.startingPromotionService = startingPromotionService;
        this.snapshotLoader = snapshotLoader;
        this.checker = checker;
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

        // Steps 1-2 (shared): read the stores and fetch Enterprise + Local into the album
        Map<Integer, Store> stores = snapshotLoader.loadAll(progress);

        // 3. Promotions starting today, from the database
        progress.setStage("Reading starting promotions");
        log.info("Step 3: Reading promotions starting today...");
        List<Promotion> starting = startingPromotionService.getStartingToday();
        log.info("{} promos starting today", starting.size());

        // 4. Check each starting promo's status in the album
        progress.setStage("Checking statuses");
        log.info("Step 4: Checking starting promotions...");
        List<PromotionCheck> report = checker.check(starting, stores);

        Map<PromoStatus, Long> counts = report.stream()
                .flatMap(r -> r.stores().stream())
                .flatMap(s -> Stream.of(s.enterprise(), s.local()))
                .collect(Collectors.groupingBy(s -> s,
                        () -> new EnumMap<>(PromoStatus.class), Collectors.counting()));
        log.info("Status counts (Enterprise + Local): {}", counts);

        // Swap in the new results all at once, so the page never sees a half-finished report
        this.startingReport = report;
        this.lastRefreshed = Instant.now();
        log.info("Refresh done.");
    }

    // What the page needs to show progress
    public record RefreshStatus(boolean running, String stage, int done, int total, String lastError) {}

    public RefreshStatus getStatus() {
        return new RefreshStatus(running.get(), progress.getStage(),
                progress.getDone(), progress.getTotal(), lastError);
    }

    public List<PromotionCheck> getStartingReport() { return startingReport; }

    public Instant getLastRefreshed() { return lastRefreshed; }
}