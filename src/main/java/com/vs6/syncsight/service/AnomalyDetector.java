package com.vs6.syncsight.service;

import com.vs6.syncsight.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AnomalyDetector {

    // Go through the whole report and keep only the promos/stores with problems
    public List<PromoAnomaly> detect(List<StartingPromotionStatus> report) {
        List<PromoAnomaly> result = new ArrayList<>();

        for (StartingPromotionStatus entry : report) {
            StartingPromotion promo = entry.promo();
            // Dates in the database make sense when ActiveDateFrom <= ActiveDateTo
            boolean datesOk = !promo.endDate().isBefore(promo.startDate());

            List<StoreAnomaly> problemStores = new ArrayList<>();
            for (StoreStatusRow row : entry.stores()) {
                List<AnomalyIssue> issues = new ArrayList<>();
                check("Enterprise", row.enterprise(), datesOk, issues);
                check("Local", row.local(), datesOk, issues);
                if (!issues.isEmpty()) {
                    problemStores.add(new StoreAnomaly(row, issues));
                }
            }

            if (!problemStores.isEmpty()) {
                result.add(new PromoAnomaly(promo, problemStores));
            }
        }

        // CRITICAL promos first (the sort keeps the original order inside each group)
        result.sort(Comparator.comparing(PromoAnomaly::severity));
        return result;
    }

    // Look at ONE status (Enterprise or Local) of ONE store and note any problem
    private void check(String source, PromoStatus status, boolean datesOk, List<AnomalyIssue> issues) {
        switch (status) {
            case NO_LOCAL_SITE -> { }   // not a problem

            case CONNECTION_ERROR, API_ERROR ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": " + status.label()));

            case UNKNOWN ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": unknown status"));

            default -> {
                if (!datesOk) {
                    // CRITICAL rule 1: the promo's dates are backwards, so it should be
                    // Cancelled, Missing or Inactive in the stores
                    if (status != PromoStatus.CANCELLED
                            && status != PromoStatus.MISSING
                            && status != PromoStatus.INACTIVE) {
                        issues.add(new AnomalyIssue(Severity.CRITICAL,
                                source + ": " + status.label() + " but the end date is before the start date"));
                    }
                } else if (status == PromoStatus.MISSING) {
                    // CRITICAL rule 2
                    issues.add(new AnomalyIssue(Severity.CRITICAL, source + ": Missing"));
                } else if (status != PromoStatus.ACTIVE) {
                    // CRITICAL rule 3: New, Inactive or Cancelled when it should be Active
                    issues.add(new AnomalyIssue(Severity.CRITICAL,
                            source + ": " + status.label() + " (expected Active)"));
                }
            }
        }
    }
}