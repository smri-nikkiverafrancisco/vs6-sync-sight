package com.vs6.syncsight.promotions.shared.anomaly;

import com.vs6.syncsight.promotions.shared.model.Promotion;
import com.vs6.syncsight.promotions.shared.model.PromotionCheck;
import com.vs6.syncsight.promotions.shared.model.StoreStatusRow;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AnomalyDetector {

    // Go through the whole report and keep only the promos/stores with problems
    public List<PromoAnomaly> detect(List<PromotionCheck> report) {
        List<PromoAnomaly> result = new ArrayList<>();

        for (PromotionCheck entry : report) {
            Promotion promo = entry.promo();
            // Dates in the database make sense when ActiveDateFrom <= ActiveDateTo
            boolean datesOk = !promo.endDate().isBefore(promo.startDate());

            List<StoreAnomaly> problemStores = new ArrayList<>();
            for (StoreStatusRow row : entry.stores()) {
                List<AnomalyIssue> issues = new ArrayList<>();
                check("Enterprise", row.enterprise(), datesOk, issues);
                check("Local", row.local(), datesOk, issues);
                if (!issues.isEmpty()) {
                    problemStores.add(new StoreAnomaly(row, issues));
                }
            }

            if (!problemStores.isEmpty()) {
                result.add(new PromoAnomaly(promo, problemStores));
            }
        }

        // CRITICAL promos first (the sort keeps the original order inside each group)
        result.sort(Comparator.comparing(PromoAnomaly::severity));
        return result;
    }

    // Flip the list: from "promos with their stores" to "stores with their promos"
    public List<StoreAnomalyGroup> groupByStore(List<PromoAnomaly> anomalies) {
        Map<Integer, List<PromoStoreAnomaly>> byStore = new TreeMap<>();   // sorted by store ID
        Map<Integer, String> names = new HashMap<>();

        for (PromoAnomaly promoAnomaly : anomalies) {
            for (StoreAnomaly storeAnomaly : promoAnomaly.stores()) {
                int storeId = storeAnomaly.store().storeId();
                names.putIfAbsent(storeId, storeAnomaly.store().storeName());
                byStore.computeIfAbsent(storeId, k -> new ArrayList<>())
                        .add(new PromoStoreAnomaly(promoAnomaly.promo(), storeAnomaly));
            }
        }

        List<StoreAnomalyGroup> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<PromoStoreAnomaly>> e : byStore.entrySet()) {
            List<PromoStoreAnomaly> promos = e.getValue();
            // Inside a store: CRITICAL first, then by promo ID
            promos.sort(Comparator
                    .comparing((PromoStoreAnomaly p) -> p.anomaly().severity())
                    .thenComparingInt(p -> p.promo().promoId()));
            groups.add(new StoreAnomalyGroup(e.getKey(), names.get(e.getKey()), promos));
        }

        // CRITICAL stores first, then by store ID (the TreeMap already ordered them by ID)
        groups.sort(Comparator.comparing(StoreAnomalyGroup::severity));
        return groups;
    }

    // Look at ONE status (Enterprise or Local) of ONE store and note any problem
    private void check(String source, PromoStatus status, boolean datesOk, List<AnomalyIssue> issues) {
        switch (status) {
            case NO_LOCAL_SITE -> { }   // not a problem

            case CONNECTION_ERROR, API_ERROR ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": " + status.label()));

            case UNKNOWN ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": unknown status"));

            default -> {
                if (!datesOk) {
                    // CRITICAL rule 1: the promo's dates are backwards, so it should be
                    // Cancelled, Missing or Inactive in the stores
                    if (status != PromoStatus.CANCELLED
                            && status != PromoStatus.MISSING
                            && status != PromoStatus.INACTIVE) {
                        issues.add(new AnomalyIssue(Severity.CRITICAL,
                                source + ": " + status.label() + " but the end date is before the start date"));
                    }
                } else if (status == PromoStatus.MISSING) {
                    // CRITICAL rule 2
                    issues.add(new AnomalyIssue(Severity.CRITICAL, source + ": Missing"));
                } else if (status != PromoStatus.ACTIVE) {
                    // CRITICAL rule 3: New, Inactive or Cancelled when it should be Active
                    issues.add(new AnomalyIssue(Severity.CRITICAL,
                            source + ": " + status.label() + " (expected Active)"));
                }
            }
        }
    }
}