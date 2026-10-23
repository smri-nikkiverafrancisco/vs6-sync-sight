package com.vs6.syncsight.promotions.shared.model;

import java.time.LocalDate;
import java.util.List;

public record Promotion(
        int promoId,
        String memoNumber,
        LocalDate startDate,
        LocalDate endDate,
        int storeCount,
        List<Integer> storeIds,
        int durationDays,
        String durationText) {}