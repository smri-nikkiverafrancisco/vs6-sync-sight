package com.vs6.syncsight.promotions.shared.sync;

import com.vs6.syncsight.promotions.shared.check.PromotionChecker;
import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.model.PromotionCheck;
import com.vs6.syncsight.promotions.shared.snapshot.RefreshProgress;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import com.vs6.syncsight.shared.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What every Promotions submodule (Starting, All Ongoing, Upcoming, Ended) has in common:
 * it reads ITS OWN list of promotions from the database, checks that list against the shared
 * album (the Enterprise + Local data), and keeps the report for its page.
 *
 * It does NOT fetch anything from the stores and it does NOT start itself.
 * PromotionRefreshCoordinator fills the album once and then calls refreshFrom() on every submodule.
 */
public abstract class AbstractPromotionSyncService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final PromotionChecker checker;

    // What the page shows
    private volatile List<PromotionCheck> report = List.of();
    private volatile Instant lastRefreshed;

    protected AbstractPromotionSyncService(PromotionChecker checker) {
        this.checker = checker;
    }

    // THE ONLY THING A SUBMODULE MUST PROVIDE: which promotions to check (its own SQL)
    protected abstract List<Promotion> loadPromotions();

    // Short wording for the log lines, for example "starting today" or "ongoing today"
    protected abstract String description();

    // Called by the coordinator after the album is full
    public void refreshFrom(Map<Integer, Store> stores, RefreshProgress progress, Instant retrievedAt) {
        // 3. This submodule's promotions, from the database
        progress.setStage("Checking promotions " + description());
        log.info("Step 3: Reading promotions {}...", description());
        List<Promotion> promotions = loadPromotions();
        log.info("{} promos {}", promotions.size(), description());

        // 4. Check each promo's status in the album
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
        this.lastRefreshed = retrievedAt;
        log.info("Done: promotions {}.", description());
    }

    public List<PromotionCheck> getReport() { return report; }

    public Instant getLastRefreshed() { return lastRefreshed; }
}