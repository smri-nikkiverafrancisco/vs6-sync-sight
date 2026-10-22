package com.vs6.syncsight.promotions.shared.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FindByFilterResponse(
        List<StorePromotion> data,
        int totalSize,
        int pageOffset,
        int pageSize,
        int currentPageSize) {}