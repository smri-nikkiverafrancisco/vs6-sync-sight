package com.vs6.syncsight.elements.shared.model;

// The result of asking ONE server for ONE promo's element count in ONE store
public record ElementCount(Status status, long total) {

    public enum Status { OK, API_ERROR, CONNECTION_ERROR, TIMEOUT, NO_LOCAL_SITE }

    public static ElementCount ok(long total) { return new ElementCount(Status.OK, total); }
    public static ElementCount of(Status status) { return new ElementCount(status, 0); }
    public boolean isOk() { return status == Status.OK; }
}