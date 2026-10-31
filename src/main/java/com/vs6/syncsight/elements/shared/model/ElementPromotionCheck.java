package com.vs6.syncsight.elements.shared.model;

import java.util.List;

// One promo with all its store rows (what the page shows)
public record ElementPromotionCheck(ElementPromotion promo, List<ElementStoreRow> stores) {
}