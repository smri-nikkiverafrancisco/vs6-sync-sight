package com.vs6.syncsight.promotions.shared.api;

public record FindByFilterRequest(int pageOffset, int pageSize, StorePromotionFilter filter) {}