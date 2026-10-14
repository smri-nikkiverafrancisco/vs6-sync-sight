package com.vs6.syncsight.model;

import java.util.List;

public record StartingPromotionStatus(StartingPromotion promo, List<StoreStatusRow> stores) {}