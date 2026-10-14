package com.vs6.syncsight.model;

import java.time.Instant;
import java.util.List;

public record FetchResult(Status status, Instant fetchedAt, List<StorePromotion> promotions, String message) {

    public enum Status { OK, CONNECTION_ERROR, API_ERROR, NO_LOCAL_SITE }

    public static FetchResult ok(List<StorePromotion> promos) {
        return new FetchResult(Status.OK, Instant.now(), List.copyOf(promos), null);
    }

    public static FetchResult connectionError(String message) {
        return new FetchResult(Status.CONNECTION_ERROR, Instant.now(), List.of(), message);
    }

    public static FetchResult apiError(String message) {
        return new FetchResult(Status.API_ERROR, Instant.now(), List.of(), message);
    }

    public static FetchResult noLocalSite() {
        return new FetchResult(Status.NO_LOCAL_SITE, Instant.now(), List.of(), "No local site");
    }

    // Short text for the logs: the count if OK, otherwise the problem
    public String summary() {
        return status == Status.OK ? String.valueOf(promotions.size()) : status.name();
    }
}