package com.vs6.syncsight.promotions.ended.anomaly;

import com.vs6.syncsight.promotions.shared.anomaly.AnomalyDetector;
import com.vs6.syncsight.promotions.shared.anomaly.AnomalyIssue;
import com.vs6.syncsight.promotions.shared.anomaly.Severity;
import com.vs6.syncsight.promotions.shared.status.PromoStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anomaly rules for ENDED promotions (module 1.3).
 *
 * An ended promo should no longer be running in the stores.
 *   Anomaly (Critical): Active or New in Enterprise and/or Local.
 *   Good: Missing, or Inactive.
 * Everything else (the grouping, sorting and the rest) is inherited from AnomalyDetector.
 */
@Service
public class EndedAnomalyDetector extends AnomalyDetector {

    @Override
    protected void check(String source, PromoStatus status, boolean datesOk, List<AnomalyIssue> issues) {
        switch (status) {
            case ACTIVE, NEW ->
                    issues.add(new AnomalyIssue(Severity.CRITICAL,
                            source + ": " + status.label() + " (promo already ended)"));

            // Could not be checked: same warnings as the other pages
            case CONNECTION_ERROR, API_ERROR ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": " + status.label()));

            case UNKNOWN ->
                    issues.add(new AnomalyIssue(Severity.WARNING, source + ": unknown status"));

            // Missing and Inactive are the good states. NO_LOCAL_SITE and Cancelled are not anomalies here.
            default -> { }
        }
    }
}