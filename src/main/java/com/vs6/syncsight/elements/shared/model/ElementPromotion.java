package com.vs6.syncsight.elements.shared.model;

import java.time.LocalDate;
import java.util.List;

public record ElementPromotion(
        int promoId,
        String memoNumber,
        LocalDate startDate,
        LocalDate endDate,
        int storeCount,
        List<Integer> storeIds,
        long elementCount,          // how many elements the DATABASE says this promo has
        int durationDays,
        String durationText) {
}