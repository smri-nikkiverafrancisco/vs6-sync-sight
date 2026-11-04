package com.vs6.syncsight.elements.shared.model;

public final class ElementStatusEvaluator {

    // Only stores with an ID BELOW this number get a status for now
    public static final int STATUS_STORES_BELOW = 600;

    private ElementStatusEvaluator() {}

    public static ElementStatus evaluate(int storeId, long dbCount,
                                         ElementCount enterprise, ElementCount local) {
        if (storeId >= STATUS_STORES_BELOW) {
            return ElementStatus.NOT_APPLICABLE;
        }
        if (enterprise.status() == ElementCount.Status.NO_ELEMENTS
                || local.status() == ElementCount.Status.NO_ELEMENTS) {
            return ElementStatus.NO_ELEMENTS;
        }
        if (!enterprise.isOk()) {
            return ElementStatus.INCOMPLETE;
        }
        // No local site: nothing to compare with, and it is not an error
        if (local.status() == ElementCount.Status.NO_LOCAL_SITE) {
            return ElementStatus.NOT_APPLICABLE;
        }
        if (!local.isOk()) {
            return ElementStatus.INCOMPLETE;
        }

        boolean enterpriseMatchesDb = enterprise.total() == dbCount;
        boolean localMatchesEnterprise = local.total() == enterprise.total();

        if (enterpriseMatchesDb && localMatchesEnterprise) return ElementStatus.MATCHED;
        if (!enterpriseMatchesDb && localMatchesEnterprise) return ElementStatus.SOURCE_MISMATCH;
        if (enterpriseMatchesDb) return ElementStatus.NOT_TALLY;    // Enterprise = DB, Local differs
        return ElementStatus.MISMATCH;
    }
}