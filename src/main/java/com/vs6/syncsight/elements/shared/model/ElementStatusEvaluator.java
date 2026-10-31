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
        if (!enterprise.isOk() || !local.isOk()) {
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