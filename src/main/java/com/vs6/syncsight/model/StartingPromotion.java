package com.vs6.syncsight.model;

import java.time.LocalDate;
import java.util.List;

public record StartingPromotion(
        int promoId,
        String memoNumber,
        LocalDate startDate,
        LocalDate endDate,
        int storeCount,
        List<Integer> storeIds,
        int durationDays,
        String durationText) {}