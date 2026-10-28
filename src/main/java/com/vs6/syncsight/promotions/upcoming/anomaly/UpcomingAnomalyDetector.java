package com.vs6.syncsight.promotions.upcoming.anomaly;

import com.vs6.syncsight.promotions.shared.anomaly.AnomalyDetector;
import com.vs6.syncsight.promotions.shared.anomaly.AnomalyIssue;
import com.vs6.syncsight.promotions.shared.anomaly.Severity;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anomaly rules for UPCOMING promotions (module 1.2).
 *
 * An upcoming promo has not started yet, so the GOOD state is NEW in both Enterprise and Local.
 * Only these are anomalies: Missing, Active, Inactive.
 * Everything else (the grouping, sorting and the rest) is inherited from AnomalyDetector.
 */
@Service
public class UpcomingAnomalyDetector extends AnomalyDetector {

    @Override
    protected void check(String source, PromoStatus status, boolean datesOk, List<AnomalyIssue> issues) {
        switch (status) {
            case MISSING ->
                    issues.add(new AnomalyIssue(Severity.CRITICAL, source + ": Missing"));

            case ACTIVE, INACTIVE ->
                    issues.add(new AnomalyIssue(Severity.CRITICAL,
                            source + ": " + status.label() + " (expected New)"));

            // Could not be checked: same warnings as the other pages
            case CONNECTION_ERROR, API_ERROR ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": " + status.label()));

            case UNKNOWN ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": unknown status"));

            // NEW is the good state. NO_LOCAL_SITE and CANCELLED are not anomalies for Upcoming.
            default -> { }
        }
    }
}