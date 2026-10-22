package com.vs6.syncsight.promotions.shared.api;

import com.vs6.syncsight.shared.exception.ApiResponseException;
import com.vs6.syncsight.shared.exception.ConnectionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class StorePromotionService {

    private static final Logger log = LoggerFactory.getLogger(StorePromotionService.class);
    private static final String FIND_BY_FILTER = "/api/catalog/storepromotions/findbyfilter";

    private final WebClient webClient;
    private final Duration timeout;          // the stopwatch

    public StorePromotionService(
            WebClient webClient,
            @Value("${vs6-enterprise.api.timeout-seconds:60}") int timeoutSeconds) {
        this.webClient = webClient;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    // "Go to THIS address, show THIS wristband, get promotions for THIS store."
    public List<StorePromotion> getPromotions(String baseUrl, String accessToken, int storeId) {
        try {
            FindByFilterResponse response = webClient.post()
                    .uri(baseUrl + FIND_BY_FILTER)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new FindByFilterRequest(0, 15000, new StorePromotionFilter(storeId)))
                    .retrieve()
                    .bodyToMono(FindByFilterResponse.class)
                    .timeout(timeout)                                    // hang up if it takes too long
                    .onErrorMap(TimeoutException.class, e -> new ConnectionFailedException(
                            "Store " + storeId + " [" + baseUrl + "]: no answer after "
                                    + timeout.toSeconds() + " seconds", e))
                    .block();

            if (response == null || response.data() == null) {
                return List.of();
            }

            // Safety check: did we get everything the server says it has?
            if (response.data().size() != response.totalSize()) {
                log.warn("Store {} [{}]: got {} promos but server says totalSize = {}",
                        storeId, baseUrl, response.data().size(), response.totalSize());
            }
            return response.data();

        } catch (WebClientResponseException e) {
            throw new ApiResponseException(
                    "Store " + storeId + " [" + baseUrl + "]: server answered "
                            + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            throw new ConnectionFailedException("Cannot reach [" + baseUrl + "]", e);
        }
    }
}