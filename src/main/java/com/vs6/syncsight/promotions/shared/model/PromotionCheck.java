package com.vs6.syncsight.promotions.shared.model;

import java.util.List;

public record PromotionCheck(Promotion promo, List<StoreStatusRow> stores) {}