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
        String durationText,
        String daysLeftText) {   // only filled by All Ongoing; null on the other pages

    // The original 8-value form, so Starting, Upcoming and Ended keep working unchanged
    public Promotion(int promoId, String memoNumber, LocalDate startDate, LocalDate endDate,
                     int storeCount, List<Integer> storeIds, int durationDays, String durationText) {
        this(promoId, memoNumber, startDate, endDate, storeCount, storeIds, durationDays, durationText, null);
    }
}