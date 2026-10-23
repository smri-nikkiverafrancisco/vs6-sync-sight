package com.vs6.syncsight.promotions.ongoing.starting.model;

import java.util.List;

public record StartingPromotionStatus(StartingPromotion promo, List<StoreStatusRow> stores) {}