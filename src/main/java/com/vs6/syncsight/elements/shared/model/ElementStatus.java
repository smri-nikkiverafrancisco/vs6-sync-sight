package com.vs6.syncsight.elements.shared.model;

// The verdict for one promo in one store, and what colour each count gets
public enum ElementStatus {

    MATCHED("Matched", "green", "green"),
    SOURCE_MISMATCH("Source Mismatch", "amber", "green"),
    NOT_TALLY("Not Tally", "green", "amber"),
    MISMATCH("Mismatch", "amber", "amber"),     // none of the three rules above fits
    INCOMPLETE("Incomplete", "", ""),           // a count could not be fetched
    NOT_APPLICABLE("", "", "");                 // stores that have no status yet

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
}