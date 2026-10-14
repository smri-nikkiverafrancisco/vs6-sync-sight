package com.vs6.syncsight.model;

public enum PromoStatus {
    CONNECTION_ERROR("Connection Error"),
    API_ERROR("API Error"),
    CANCELLED("Cancelled"),
    NEW("New"),
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    MISSING("Missing"),
    NO_LOCAL_SITE("N/A"),
    UNKNOWN("Unknown");

    private final String label;

    PromoStatus(String label) {
        this.label = label;
    }

    // The words shown on the page
    public String label() {
        return label;
    }
}