package com.vs6.syncsight.promotions.shared.check;

import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.model.PromotionCheck;
import com.vs6.syncsight.promotions.shared.model.StoreStatusRow;
import com.vs6.syncsight.promotions.shared.api.StorePromotion;
import com.vs6.syncsight.promotions.shared.snapshot.PromotionSnapshotStore;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import com.vs6.syncsight.shared.source.FetchResult;
import com.vs6.syncsight.shared.source.Source;
import com.vs6.syncsight.shared.store.Store;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PromotionChecker {

    private final PromotionSnapshotStore snapshotStore;

    public PromotionChecker(PromotionSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    // For each starting promo, look up each of its stores in the album
    public List<PromotionCheck> check(List<Promotion> starting, Map<Integer, Store> stores) {
        List<PromotionCheck> report = new ArrayList<>();

        for (Promotion promo : starting) {
            List<StoreStatusRow> rows = new ArrayList<>();
            for (int storeId : promo.storeIds()) {
                Store store = stores.get(storeId);
                String storeName = store != null ? store.storeName() : "Unknown store";
                rows.add(new StoreStatusRow(
                        storeId,
                        storeName,
                        resolve(Source.ENTERPRISE, storeId, promo.promoId()),
                        resolve(Source.LOCAL, storeId, promo.promoId())));
            }
            report.add(new PromotionCheck(promo, rows));
        }
        return report;
    }

    // The status of ONE promo in ONE store at ONE source
    private PromoStatus resolve(Source source, int storeId, int promoId) {
        Optional<FetchResult> entry = snapshotStore.get(source, storeId);
        if (entry.isEmpty()) {
            return PromoStatus.UNKNOWN;                 // nothing in the album for this store
        }

        FetchResult result = entry.get();
        switch (result.status()) {
            case CONNECTION_ERROR: return PromoStatus.CONNECTION_ERROR;
            case API_ERROR:        return PromoStatus.API_ERROR;
            case NO_LOCAL_SITE:    return PromoStatus.NO_LOCAL_SITE;
            default: break;                             // OK: look inside the box
        }

        StorePromotion found = result.promotions().stream()
                .filter(p -> p.promoId() == promoId)
                .findFirst()
                .orElse(null);

        if (found == null) {
            return PromoStatus.MISSING;
        }
        if (found.validFrom() != null && found.validTo() != null
                && found.validTo().isBefore(found.validFrom())) {
            return PromoStatus.CANCELLED;
        }

        String code = found.status() == null ? "" : found.status().trim();
        return switch (code) {
            case "0" -> PromoStatus.NEW;
            case "1" -> PromoStatus.ACTIVE;
            case "9" -> PromoStatus.INACTIVE;
            default  -> PromoStatus.UNKNOWN;            // some other status code
        };
    }
}