package com.vs6.syncsight.service;

import com.vs6.syncsight.model.FindByFilterRequest;
import com.vs6.syncsight.model.FindByFilterResponse;
import com.vs6.syncsight.model.StorePromotion;
import com.vs6.syncsight.model.StorePromotionFilter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
public class StorePromotionService {

    private static final String FIND_BY_FILTER = "/api/catalog/storepromotions/findbyfilter";

    private final WebClient webClient;

    public StorePromotionService(WebClient webClient) {
        this.webClient = webClient;
    }

    // "Go to THIS address, show THIS wristband, get promotions for THIS store."
    public List<StorePromotion> getPromotions(String baseUrl, String accessToken, int storeId) {
        try {
            FindByFilterResponse response = webClient.post()
                    .uri(baseUrl + FIND_BY_FILTER)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new FindByFilterRequest(0, 10000, new StorePromotionFilter(storeId)))
                    .retrieve()
                    .bodyToMono(FindByFilterResponse.class)
                    .block();

            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data();

        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "Store " + storeId + " [" + baseUrl + "]: server answered "
                            + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            throw new IllegalStateException("Cannot reach [" + baseUrl + "]", e);
        }
    }
}