package com.vs6.syncsight.model;

public record FindByFilterRequest(int pageOffset, int pageSize, StorePromotionFilter filter) {}