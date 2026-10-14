package com.vs6.syncsight.service;

import com.vs6.syncsight.exception.ApiResponseException;
import com.vs6.syncsight.exception.ConnectionFailedException;
import com.vs6.syncsight.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;                       // NEW
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;             // NEW
import java.util.stream.Stream;                 // NEW

@Service
public class PromotionSyncService {

    private static final Logger log = LoggerFactory.getLogger(PromotionSyncService.class);

    private final StartingPromotionService startingPromotionService;
    private final StoreService storeService;
    private final StorePromotionService promotionService;
    private final AuthTokenService authTokenService;
    private final PromotionSnapshotStore snapshotStore;
    private final StartingPromotionChecker checker;                    // NEW
    private final String enterpriseUrl;

    // What the page will show later
    private volatile List<StartingPromotionStatus> startingReport = List.of();   // CHANGED (was List<StartingPromotion>)
    private volatile Instant lastRefreshed;

    public PromotionSyncService(
            StartingPromotionService startingPromotionService,
            StoreService storeService,
            StorePromotionService promotionService,
            AuthTokenService authTokenService,
            PromotionSnapshotStore snapshotStore,
            StartingPromotionChecker checker,                          // NEW
            @Value("${vs6-enterprise.api.base-url}") String enterpriseUrl) {
        this.startingPromotionService = startingPromotionService;
        this.storeService = storeService;
        this.promotionService = promotionService;
        this.authTokenService = authTokenService;
        this.snapshotStore = snapshotStore;
        this.checker = checker;                                        // NEW
        this.enterpriseUrl = enterpriseUrl;
    }

    // "Refresh everything." Only one refresh can run at a time (synchronized).
    public synchronized void refresh() {

        // 1. All stores from the database (one card per store ID, sorted)
        log.info("Step 1: Reading stores from database...");
        Map<Integer, Store> stores = new TreeMap<>();
        for (Store s : storeService.getStores()) {
            stores.putIfAbsent(s.storeId(), s);
        }
        log.info("Found {} stores", stores.size());

        // 2. Fetch promotions of every store from Enterprise and Local, keep them in the album
        log.info("Step 2: Fetching promotions from Enterprise and Local...");
        for (Store store : stores.values()) {
            int storeId = store.storeId();

            FetchResult enterprise = fetch(enterpriseUrl, storeId);
            FetchResult local = store.hasLocalSite()
                    ? fetch(store.localUrl(), storeId)
                    : FetchResult.noLocalSite();

            snapshotStore.save(Source.ENTERPRISE, storeId, enterprise);
            snapshotStore.save(Source.LOCAL, storeId, local);

            log.info("Store {} ({}) : Enterprise = {} | Local = {}",
                    storeId, store.storeName(), enterprise.summary(), local.summary());
        }

        // 3. Promotions starting today, from the database
        log.info("Step 3: Reading promotions starting today...");
        List<StartingPromotion> starting = startingPromotionService.getStartingToday();
        log.info("{} promos starting today", starting.size());

        // 4. Check each starting promo's status in the album                      // NEW (whole step)
        log.info("Step 4: Checking starting promotions...");
        List<StartingPromotionStatus> report = checker.check(starting, stores);

        // A quick summary for the logs: how many checks ended in each status
        Map<PromoStatus, Long> counts = report.stream()
                .flatMap(r -> r.stores().stream())
                .flatMap(s -> Stream.of(s.enterprise(), s.local()))
                .collect(Collectors.groupingBy(s -> s,
                        () -> new EnumMap<>(PromoStatus.class), Collectors.counting()));
        log.info("Status counts (Enterprise + Local): {}", counts);

        this.startingReport = report;                                  // CHANGED (was this.startingPromotions = starting)
        this.lastRefreshed = Instant.now();
        log.info("Refresh done.");
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

    public List<StartingPromotionStatus> getStartingReport() { return startingReport; }   // CHANGED

    public Instant getLastRefreshed() { return lastRefreshed; }
}