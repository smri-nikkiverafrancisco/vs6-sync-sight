package com.vs6.syncsight.promotions.shared.anomaly;

import com.vs6.syncsight.promotions.ongoing.starting.model.StartingPromotion;

public record PromoStoreAnomaly(StartingPromotion promo, StoreAnomaly anomaly) {}