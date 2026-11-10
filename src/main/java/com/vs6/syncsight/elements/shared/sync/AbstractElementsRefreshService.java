package com.vs6.syncsight.elements.shared.sync;

import com.vs6.syncsight.elements.shared.api.ElementCountClient;
import com.vs6.syncsight.elements.shared.model.ElementCount;
import com.vs6.syncsight.elements.shared.model.ElementPromotion;
import com.vs6.syncsight.elements.shared.model.ElementPromotionCheck;
import com.vs6.syncsight.elements.shared.model.ElementStoreRow;
import com.vs6.syncsight.promotions.shared.snapshot.RefreshProgress;
import com.vs6.syncsight.promotions.shared.sync.RefreshStatus;
import com.vs6.syncsight.shared.auth.AuthTokenService;
import com.vs6.syncsight.shared.exception.ApiResponseException;
import com.vs6.syncsight.shared.exception.ConnectionFailedException;
import com.vs6.syncsight.shared.source.Source;
import com.vs6.syncsight.shared.store.Store;
import com.vs6.syncsight.shared.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The refresh that is the same for every Elements page (Starting, All Ongoing, ...):
 *   1. the page's promos (with the DATABASE element count) and the stores
 *   2. the Enterprise element count of every promo in every one of its stores
 *   3. the Local element count of every promo in every one of its stores
 * A page only says WHICH promos it wants, by filling in loadPromos().
 */
public abstract class AbstractElementsRefreshService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String name;                       // for the log lines, e.g. "Elements/Starting"
    private final StoreService storeService;
    private final ElementCountClient client;
    private final AuthTokenService authTokenService;
    private final String enterpriseUrl;
    private final int threads;                       // how many "cooks" work at the same time

    // This page's own album of counts
    private final ElementCountStore countStore = new ElementCountStore();

    // Progress board (read by the page)
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean everStarted = new AtomicBoolean(false);
    private final RefreshProgress progress = new RefreshProgress();
    private volatile String lastError;

    // The latest finished report
    private volatile List<ElementPromotionCheck> report = List.of();
    private volatile Instant lastRefreshed;

    protected AbstractElementsRefreshService(String name,
                                             StoreService storeService,
                                             ElementCountClient client,
                                             AuthTokenService authTokenService,
                                             String enterpriseUrl,
                                             int threads) {
        this.name = name;
        this.storeService = storeService;
        this.client = client;
        this.authTokenService = authTokenService;
        this.enterpriseUrl = enterpriseUrl;
        this.threads = threads;
    }

    // "Which promos does this page want?" (each page answers with its own SQL)
    protected abstract List<ElementPromotion> loadPromos();

    // Called whenever the page is opened: starts the very first refresh, only once.
    public void startIfNeverStarted() {
        if (!everStarted.get()) {
            startRefresh();
        }
    }

    // BACKGROUND: "Start a refresh and come back to me right away." Returns false if one is already running.
    public boolean startRefresh() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        everStarted.set(true);
        Thread worker = new Thread(() -> {
            try {
                refresh();
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("{} refresh failed: {}", name, e.getMessage());
            } finally {
                progress.setStage("Idle");
                running.set(false);
            }
        }, "elements-refresh-worker");
        worker.start();
        return true;
    }

    // Forget the old report (used when a page is switched to other data)
    protected void clearReport() {
        report = List.of();
        lastRefreshed = null;
    }

    public List<ElementPromotionCheck> getReport() { return report; }

    public Instant getLastRefreshed() { return lastRefreshed; }

    public RefreshStatus getStatus() {
        return new RefreshStatus(running.get(), progress.getStage(),
                progress.getDone(), progress.getTotal(), lastError);
    }

    private void refresh() {
        long started = System.currentTimeMillis();
        lastError = null;
        progress.reset();

        // 1. The page's promos from the database, and the stores
        progress.setStage("Reading promos and stores");
        log.info("{}: reading the promos and the stores...", name);
        List<ElementPromotion> promos = loadPromos();
        Map<Integer, Store> stores = new TreeMap<>();
        for (Store s : storeService.getStores()) {
            stores.putIfAbsent(s.storeId(), s);
        }

        // Flip "promos with their stores" into "stores with their promos"
        Map<Integer, List<ElementPromotion>> promosByStore = new TreeMap<>();
        for (ElementPromotion promo : promos) {
            for (int storeId : promo.storeIds()) {
                promosByStore.computeIfAbsent(storeId, k -> new ArrayList<>()).add(promo);
            }
        }
        int pairs = promosByStore.values().stream().mapToInt(List::size).sum();
        log.info("{}: {} promos across {} stores = {} promo-store pairs ({} at a time)",
                name, promos.size(), promosByStore.size(), pairs, threads);

        // 2 + 3. PARALLEL: one worker per store counts Enterprise, then Local, for each of its promos
        progress.setStage("Counting elements");
        progress.setTotal(promosByStore.size());
        countStore.clear();
        AtomicInteger storesDone = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Void>> tasks = promosByStore.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        countOneStore(entry.getKey(), entry.getValue(), stores.get(entry.getKey()));
                        progress.storeDone();
                        int done = storesDone.incrementAndGet();
                        if (done % 10 == 0 || done == promosByStore.size()) {
                            log.info("{}: {}/{} stores done", name, done, promosByStore.size());
                        }
                    }, pool))
                    .toList();
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } finally {
            pool.shutdown();
        }

        // Build the report for the page from the album
        List<ElementPromotionCheck> newReport = promos.stream()
                .map(promo -> toCheck(promo, stores))
                .toList();
        report = newReport;
        lastRefreshed = Instant.now();

        List<ElementStoreRow> rows = newReport.stream().flatMap(c -> c.stores().stream()).toList();
        long entOk = rows.stream().filter(r -> r.enterprise().isOk()).count();
        long locOk = rows.stream().filter(r -> r.local().isOk()).count();
        long noSite = rows.stream()
                .filter(r -> r.local().status() == ElementCount.Status.NO_LOCAL_SITE).count();
        log.info("{}: done in {} s: {} promos, {} store rows, Enterprise OK {}, Local OK {}, "
                        + "no local site {}, others {}",
                name, (System.currentTimeMillis() - started) / 1000, promos.size(), rows.size(),
                entOk, locOk, noSite, rows.size() * 2L - entOk - locOk - noSite);
    }

    // The job of ONE worker: one store, all of that store's promos, one after the other
    private void countOneStore(int storeId, List<ElementPromotion> storePromos, Store store) {
        String localUrl = (store != null && store.hasLocalSite()) ? store.localUrl() : null;

        for (ElementPromotion promo : storePromos) {
            long promoId = promo.promoId();
            countStore.save(Source.ENTERPRISE, storeId, promoId, count(enterpriseUrl, storeId, promoId));
            countStore.save(Source.LOCAL, storeId, promoId,
                    localUrl != null
                            ? count(localUrl, storeId, promoId)
                            : ElementCount.of(ElementCount.Status.NO_LOCAL_SITE));
        }
    }

    // One call to one address. Never throws: problems become a status.
    private ElementCount count(String baseUrl, int storeId, long promoId) {
        try {
            return client.getElementCount(baseUrl, authTokenService.getAccessToken(baseUrl), storeId, promoId);
        } catch (ConnectionFailedException e) {
            log.error(e.getMessage());
            return ElementCount.of(ElementCount.Status.CONNECTION_ERROR);
        } catch (ApiResponseException e) {
            log.error(e.getMessage());
            return ElementCount.of(ElementCount.Status.API_ERROR);
        } catch (Exception e) {
            log.error("Store {} [{}]: unexpected problem: {}", storeId, baseUrl, e.getMessage());
            return ElementCount.of(ElementCount.Status.API_ERROR);
        }
    }

    private ElementPromotionCheck toCheck(ElementPromotion promo, Map<Integer, Store> stores) {
        List<ElementStoreRow> rows = promo.storeIds().stream()
                .map(storeId -> {
                    Store store = stores.get(storeId);
                    return new ElementStoreRow(
                            storeId,
                            store != null ? store.storeName() : "Store " + storeId,
                            countStore.get(Source.ENTERPRISE, storeId, promo.promoId()),
                            countStore.get(Source.LOCAL, storeId, promo.promoId()));
                })
                .toList();
        return new ElementPromotionCheck(promo, rows);
    }
}