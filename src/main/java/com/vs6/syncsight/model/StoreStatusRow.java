package com.vs6.syncsight.model;

public record StoreStatusRow(int storeId, String storeName, PromoStatus enterprise, PromoStatus local) {}