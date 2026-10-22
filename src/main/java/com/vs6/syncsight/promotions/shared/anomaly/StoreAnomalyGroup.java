package com.vs6.syncsight.promotions.shared.anomaly;

import java.util.List;

public record StoreAnomalyGroup(int storeId, String storeName, List<PromoStoreAnomaly> promos) {

    public Severity severity() {
        return promos.stream().anyMatch(p -> p.anomaly().severity() == Severity.CRITICAL)
                ? Severity.CRITICAL : Severity.WARNING;
    }
}