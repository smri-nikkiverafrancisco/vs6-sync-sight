package com.vs6.syncsight.elements.shared.model;

// One store line under a promo: its element count in Enterprise and in Local
public record ElementStoreRow(int storeId, String storeName,
                              ElementCount enterprise, ElementCount local) {
}