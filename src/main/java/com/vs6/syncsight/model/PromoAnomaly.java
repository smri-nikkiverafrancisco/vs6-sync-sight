package com.vs6.syncsight.model;

import java.util.List;

public record PromoAnomaly(StartingPromotion promo, List<StoreAnomaly> stores) {

    public Severity severity() {
        return stores.stream().anyMatch(s -> s.severity() == Severity.CRITICAL)
                ? Severity.CRITICAL : Severity.WARNING;
    }
}