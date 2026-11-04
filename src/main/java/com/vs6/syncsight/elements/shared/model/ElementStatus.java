package com.vs6.syncsight.elements.shared.model;

// The verdict for one promo in one store, and what colour each count gets
public enum ElementStatus {

    MATCHED("Matched", "green", "green"),
    SOURCE_MISMATCH("Source Mismatch", "amber", "green"),
    NOT_TALLY("Not Tally", "green", "amber"),
    MISMATCH("Mismatch", "amber", "amber"),     // none of the rules above fits
    NO_ELEMENTS("No Elements", "", ""),         // the promo exists in the store but has no elements
    INCOMPLETE("API Error", "", ""),            // a count could not be fetched (API error, timeout, connection error)
    NOT_APPLICABLE("", "", "");                 // no status for this row

    private final String label;
    private final String enterpriseColor;
    private final String localColor;

    ElementStatus(String label, String enterpriseColor, String localColor) {
        this.label = label;
        this.enterpriseColor = enterpriseColor;
        this.localColor = localColor;
    }

    public String label() { return label; }
    public String enterpriseColor() { return enterpriseColor; }
    public String localColor() { return localColor; }

    // Shown in the Anomalies tab: everything except Matched and rows without a status
    public boolean isAnomaly() {
        return this != MATCHED && this != NOT_APPLICABLE;
    }
}