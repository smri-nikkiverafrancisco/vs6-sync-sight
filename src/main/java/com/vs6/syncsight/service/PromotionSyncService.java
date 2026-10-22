package com.vs6.syncsight.service;

import com.vs6.syncsight.promotions.shared.api.StorePromotion;
import com.vs6.syncsight.promotions.shared.api.StorePromotionService;
import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotStore;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import com.vs6.syncsight.shared.exception.ApiResponseException;
import com.vs6.syncsight.shared.exception.ConnectionFailedException;
import com.vs6.syncsight.model.*;
import com.vs6.syncsight.shared.auth.AuthTokenService;
import com.vs6.syncsight.shared.source.FetchResult;
import com.vs6.syncsight.shared.source.Source;
import com.vs6.syncsight.shared.store.Store;
import com.vs6.syncsight.shared.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PromotionSyncService {

    private static final Logger log = LoggerFactory.getLogger(PromotionSyncService.class);

    private final StartingPromotionService startingPromotionService;
    private final StoreService storeService;
    private final StorePromotionService promotionService;
    private final AuthTokenService authTokenService;
    private final PromotionSnapshotStore snapshotStore;
    private final StartingPromotionChecker checker;
    private final String enterpriseUrl;
    private final int threads;                       // how many "cooks" work at the same time

    // Progress board (read by the page)
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger storesDone = new AtomicInteger();
    private volatile int storesTotal;
    private volatile String stage = "Idle";
    private volatile String lastError;

    // What the page shows
    private volatile List<StartingPromotionStatus> startingReport = List.of();
    private volatile Instant lastRefreshed;

    public PromotionSyncService(
            StartingPromotionService startingPromotionService,
            StoreService storeService,
            StorePromotionService promotionService,
            AuthTokenService authTokenService,
            PromotionSnapshotStore snapshotStore,
            StartingPromotionChecker checker,
            @Value("${vs6-enterprise.api.base-url}") String enterpriseUrl,
            @Value("${vs6-enterprise.sync.threads:4}") int threads) {
        this.startingPromotionService = startingPromotionService;
        this.storeService = storeService;
        this.promotionService = promotionService;
        this.authTokenService = authTokenService;
        this.snapshotStore = snapshotStore;
        this.checker = checker;
        this.enterpriseUrl = enterpriseUrl;
        this.threads = threads;
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
                stage = "Idle";
                running.set(false);
            }
        }, "refresh-worker");
        worker.start();
        return true;
    }

    private void refresh() {
        lastError = null;
        storesDone.set(0);
        storesTotal = 0;

        // 1. All stores from the database (one card per store ID, sorted)
        stage = "Reading stores";
        log.info("Step 1: Reading stores from database...");
        Map<Integer, Store> stores = new TreeMap<>();
        for (Store s : storeService.getStores()) {
            stores.putIfAbsent(s.storeId(), s);
        }
        log.info("Found {} stores", stores.size());

        // 2. PARALLEL: fetch promotions of every store from Enterprise and Local into the album
        stage = "Fetching promotions";
        storesTotal = stores.size();
        log.info("Step 2: Fetching promotions from Enterprise and Local ({} at a time)...", threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Void>> tasks = stores.values().stream()
                    .map(store -> CompletableFuture.runAsync(() -> fetchStore(store), pool))
                    .toList();
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();   // wait for all
        } finally {
            pool.shutdown();
        }

        // 3. Promotions starting today, from the database
        stage = "Reading starting promotions";
        log.info("Step 3: Reading promotions starting today...");
        List<StartingPromotion> starting = startingPromotionService.getStartingToday();
        log.info("{} promos starting today", starting.size());

        // 4. Check each starting promo's status in the album
        stage = "Checking statuses";
        log.info("Step 4: Checking starting promotions...");
        List<StartingPromotionStatus> report = checker.check(starting, stores);

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

    // The job of ONE cook: handle ONE store (Enterprise, then Local)
    private void fetchStore(Store store) {
        int storeId = store.storeId();

        FetchResult enterprise = fetch(enterpriseUrl, storeId);
        FetchResult local = store.hasLocalSite()
                ? fetch(store.localUrl(), storeId)
                : FetchResult.noLocalSite();

        snapshotStore.save(Source.ENTERPRISE, storeId, enterprise);
        snapshotStore.save(Source.LOCAL, storeId, local);

        log.info("Store {} ({}) : Enterprise = {} | Local = {}   [{}/{}]",
                storeId, store.storeName(), enterprise.summary(), local.summary(),
                storesDone.incrementAndGet(), storesTotal);
    }

    // One call to one address. Never throws: problems become a note in the album.
    private FetchResult fetch(String baseUrl, int storeId) {
        try {
            List<StorePromotion> promos = promotionService.getPromotions(
                    baseUrl, authTokenService.getAccessToken(baseUrl), storeId);
            return FetchResult.ok(promos);
        } catch (ConnectionFailedException e) {
            log.error(e.getMessage());
            return FetchResult.connectionError(e.getMessage());
        } catch (ApiResponseException e) {
            log.error(e.getMessage());
            return FetchResult.apiError(e.getMessage());
        } catch (Exception e) {
            log.error("Store {} [{}]: unexpected problem: {}", storeId, baseUrl, e.getMessage());
            return FetchResult.apiError(e.getMessage());
        }
    }

    // What the page needs to show progress
    public record RefreshStatus(boolean running, String stage, int done, int total, String lastError) {}

    public RefreshStatus getStatus() {
        return new RefreshStatus(running.get(), stage, storesDone.get(), storesTotal, lastError);
    }

    public List<StartingPromotionStatus> getStartingReport() { return startingReport; }

    public Instant getLastRefreshed() { return lastRefreshed; }
}