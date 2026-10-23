package com.vs6.syncsight.promotions.shared.anomaly;

import com.vs6.syncsight.promotions.ongoing.starting.model.StartingPromotion;

import java.util.List;

public record PromoAnomaly(StartingPromotion promo, List<StoreAnomaly> stores) {

    public Severity severity() {
        return stores.stream().anyMatch(s -> s.severity() == Severity.CRITICAL)
                ? Severity.CRITICAL : Severity.WARNING;
    }
}