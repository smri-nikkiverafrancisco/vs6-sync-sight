package com.vs6.syncsight.promotions.shared.anomaly;

import com.vs6.syncsight.promotions.ongoing.starting.model.StoreStatusRow;

import java.util.List;
import java.util.stream.Collectors;

public record StoreAnomaly(StoreStatusRow store, List<AnomalyIssue> issues) {

    // A store is CRITICAL if any of its problems is CRITICAL
    public Severity severity() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.CRITICAL)
                ? Severity.CRITICAL : Severity.WARNING;
    }

    public String reasonText() {
        return issues.stream().map(AnomalyIssue::reason).collect(Collectors.joining("; "));
    }
}