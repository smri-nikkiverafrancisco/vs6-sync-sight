package com.vs6.syncsight.promotions.shared.anomaly;

import com.vs6.syncsight.promotions.shared.model.Promotion;

import java.util.List;

public record PromoAnomaly(Promotion promo, List<StoreAnomaly> stores) {

    public Severity severity() {
        return stores.stream().anyMatch(s -> s.severity() == Severity.CRITICAL)
                ? Severity.CRITICAL : Severity.WARNING;
    }
}