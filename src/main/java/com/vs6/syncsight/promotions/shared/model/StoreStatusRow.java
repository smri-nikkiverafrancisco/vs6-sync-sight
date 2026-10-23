package com.vs6.syncsight.promotions.shared.model;

import com.vs6.syncsight.promotions.shared.status.PromoStatus;

public record StoreStatusRow(int storeId, String storeName, PromoStatus enterprise, PromoStatus local) {}