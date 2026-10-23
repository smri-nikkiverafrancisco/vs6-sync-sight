package com.vs6.syncsight.promotions.shared.snapshot;

import com.vs6.syncsight.promotions.shared.api.StorePromotion;
import com.vs6.syncsight.promotions.shared.api.StorePromotionService;
import com.vs6.syncsight.shared.auth.AuthTokenService;
import com.vs6.syncsight.shared.exception.ApiResponseException;
import com.vs6.syncsight.shared.exception.ConnectionFailedException;
import com.vs6.syncsight.shared.source.FetchResult;
import com.vs6.syncsight.shared.source.Source;
import com.vs6.syncsight.shared.store.Store;
import com.vs6.syncsight.shared.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shared half of a Promotions refresh:
 * reads every store from the database, then fetches each store's promotions
 * from Enterprise and Local (several stores at the same time) into the snapshot store.
 *
 * Every Promotions submodule (Starting, All Ongoing, Upcoming, Ended) can call this
 * and then do its own check on top of the snapshot.
 */
@Service
public class PromotionSnapshotLoader {

    private static final Logger log = LoggerFactory.getLogger(PromotionSnapshotLoader.class);

    private final StoreService storeService;
    private final StorePromotionService promotionService;
    private final AuthTokenService authTokenService;
    private final PromotionSnapshotStore snapshotStore;
    private final String enterpriseUrl;
    private final int threads;                       // how many "cooks" work at the same time

    public PromotionSnapshotLoader(
            StoreService storeService,
            StorePromotionService promotionService,
            AuthTokenService authTokenService,
            PromotionSnapshotStore snapshotStore,
            @Value("${vs6-enterprise.api.base-url}") String enterpriseUrl,
            @Value("${vs6-enterprise.sync.threads:4}") int threads) {
        this.storeService = storeService;
        this.promotionService = promotionService;
        this.authTokenService = authTokenService;
        this.snapshotStore = snapshotStore;
        this.enterpriseUrl = enterpriseUrl;
        this.threads = threads;
    }

    // Reads the stores, fetches all their promotions into the snapshot store,
    // and returns the stores (one per store ID, sorted) for the caller's own checks.
    public Map<Integer, Store> loadAll(RefreshProgress progress) {
        // 1. All stores from the database (one card per store ID, sorted)
        progress.setStage("Reading stores");
        log.info("Step 1: Reading stores from database...");
        Map<Integer, Store> stores = new TreeMap<>();
        for (Store s : storeService.getStores()) {
            stores.putIfAbsent(s.storeId(), s);
        }
        log.info("Found {} stores", stores.size());

        // 2. PARALLEL: fetch promotions of every store from Enterprise and Local into the album
        progress.setStage("Fetching promotions");
        progress.setTotal(stores.size());
        log.info("Step 2: Fetching promotions from Enterprise and Local ({} at a time)...", threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Void>> tasks = stores.values().stream()
                    .map(store -> CompletableFuture.runAsync(() -> fetchStore(store, progress), pool))
                    .toList();
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();   // wait for all
        } finally {
            pool.shutdown();
        }

        return stores;
    }

    // The job of ONE cook: handle ONE store (Enterprise, then Local)
    private void fetchStore(Store store, RefreshProgress progress) {
        int storeId = store.storeId();

        FetchResult enterprise = fetch(enterpriseUrl, storeId);
        FetchResult local = store.hasLocalSite()
                ? fetch(store.localUrl(), storeId)
                : FetchResult.noLocalSite();

        snapshotStore.save(Source.ENTERPRISE, storeId, enterprise);
        snapshotStore.save(Source.LOCAL, storeId, local);

        log.info("Store {} ({}) : Enterprise = {} | Local = {}   [{}/{}]",
                storeId, store.storeName(), enterprise.summary(), local.summary(),
                progress.storeDone(), progress.getTotal());
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
}