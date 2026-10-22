package com.vs6.syncsight.promotions.shared.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StorePromotion(
        int storeId,
        int promoId,
        String externalPromoCode,
        int promoType,
        String descr1,
        String status,
        LocalDate validFrom,
        LocalDate validTo,
        int discountType,
        int processType) {}