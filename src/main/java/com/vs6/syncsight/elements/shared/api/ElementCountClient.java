package com.vs6.syncsight.elements.shared.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vs6.syncsight.elements.shared.model.ElementCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
public class ElementCountClient {

    private static final Logger log = LoggerFactory.getLogger(ElementCountClient.class);

    // "How many elements does this promo have in this store?"
    private static final String ELEMENTS_PATH = "/api/catalog/itemsinpromostore/findbyfilter";
    // "Does this promo exist in this store?"
    private static final String PROMO_IN_STORE_PATH = "/api/catalog/storepromotions/findbyfilter";

    // What we send: pageSize 1, because we only want the totalSize number
    public record ElementCountRequest(int pageOffset, int pageSize, ElementCountFilter filter) {}

    public record ElementCountFilter(int storeId, long promoId) {}

    // What we read back: only totalSize, everything else is ignored
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementCountResponse(long totalSize) {}

    // "The server is up, but did not answer in time"
    private static class ElementTimeoutException extends RuntimeException {
        ElementTimeoutException(String message, Throwable cause) { super(message, cause); }
    }

    private final WebClient webClient;
    private final Duration timeout;

    // Own timeout setting for element counts; if it is not set, the normal timeout is used
    public ElementCountClient(
            WebClient webClient,
            @Value("${vs6-enterprise.api.elements-timeout-seconds:${vs6-enterprise.api.timeout-seconds:60}}")
            int timeoutSeconds) {
        this.webClient = webClient;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    // "Go to THIS address, show THIS wristband, count the elements of THIS promo in THIS store."
    public ElementCount getElementCount(String baseUrl, String accessToken, int storeId, long promoId) {
        long started = System.nanoTime();
        try {
            ElementCountResponse response = post(ELEMENTS_PATH, baseUrl, accessToken, storeId, promoId);

            if (response == null) {
                log.warn("Store {} promo {} [{}]: empty answer ({} ms)", storeId, promoId, baseUrl, ms(started));
                return apiErrorOrNoElements(baseUrl, accessToken, storeId, promoId);
            }
            log.debug("Elements: store {} promo {} [{}] = {} ({} ms)",
                    storeId, promoId, baseUrl, response.totalSize(), ms(started));
            return ElementCount.ok(response.totalSize());

        } catch (ElementTimeoutException e) {
            log.warn("Store {} promo {} [{}]: {}", storeId, promoId, baseUrl, e.getMessage());
            return ElementCount.of(ElementCount.Status.TIMEOUT);
        } catch (WebClientResponseException e) {
            log.warn("Store {} promo {} [{}]: server answered {} ({} ms)",
                    storeId, promoId, baseUrl, e.getStatusCode().value(), ms(started));
            return apiErrorOrNoElements(baseUrl, accessToken, storeId, promoId);
        } catch (WebClientRequestException e) {
            log.warn("Store {} promo {} [{}]: cannot connect ({})",
                    storeId, promoId, baseUrl, e.getMessage());
            return ElementCount.of(ElementCount.Status.CONNECTION_ERROR);
        }
    }

    // An API error can simply mean "this promo has no elements in this store", but not always.
    // So before calling it an error, ask whether the promo exists in this store at all:
    // exists (totalSize = 1) -> No Elements, anything else -> still an API Error.
    private ElementCount apiErrorOrNoElements(String baseUrl, String accessToken, int storeId, long promoId) {
        try {
            ElementCountResponse response = post(PROMO_IN_STORE_PATH, baseUrl, accessToken, storeId, promoId);
            if (response != null && response.totalSize() == 1) {
                log.warn("Store {} promo {} [{}]: the promo exists but has no elements", storeId, promoId, baseUrl);
                return ElementCount.of(ElementCount.Status.NO_ELEMENTS);
            }
        } catch (Exception e) {
            log.warn("Store {} promo {} [{}]: could not check if the promo exists ({})",
                    storeId, promoId, baseUrl, e.getMessage());
        }
        return ElementCount.of(ElementCount.Status.API_ERROR);
    }

    // One call to one endpoint, with the same request body for both endpoints
    private ElementCountResponse post(String path, String baseUrl, String accessToken, int storeId, long promoId) {
        return webClient.post()
                .uri(baseUrl + path)
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ElementCountRequest(0, 1, new ElementCountFilter(storeId, promoId)))
                .retrieve()
                .bodyToMono(ElementCountResponse.class)
                .timeout(timeout)
                .onErrorMap(TimeoutException.class, e -> new ElementTimeoutException(
                        "no answer after " + timeout.toSeconds() + " seconds", e))
                .block();
    }

    private static long ms(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}