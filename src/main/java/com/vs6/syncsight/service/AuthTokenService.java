package com.vs6.syncsight.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vs6.syncsight.exception.ApiResponseException;
import com.vs6.syncsight.exception.ConnectionFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthTokenService {

    private final WebClient webClient;
    private final String tokenPath;
    private final String username;
    private final String password;

    // The key rack: one wristband per address (safe for many workers at once)
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();
    // One lock per address, so only workers asking for the SAME address wait for each other
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public AuthTokenService(
            WebClient webClient,
            @Value("${vs6-enterprise.api.auth.token}") String tokenPath,
            @Value("${vs6-enterprise.api.auth.username}") String username,
            @Value("${vs6-enterprise.api.auth.password}") String password) {
        this.webClient = webClient;
        this.tokenPath = tokenPath;
        this.username = username;
        this.password = password;
    }

    // "Give me a good wristband for THIS address."
    public String getAccessToken(String baseUrl) {
        CachedToken cached = tokens.get(baseUrl);
        if (isValid(cached)) {
            return cached.accessToken();
        }

        synchronized (locks.computeIfAbsent(baseUrl, k -> new Object())) {
            cached = tokens.get(baseUrl);          // someone else may have just fetched it
            if (!isValid(cached)) {
                cached = requestNewToken(baseUrl);
                tokens.put(baseUrl, cached);
            }
            return cached.accessToken();
        }
    }

    private boolean isValid(CachedToken token) {
        return token != null && System.currentTimeMillis() < token.expiresAtMillis();
    }

    private CachedToken requestNewToken(String baseUrl) {
        try {
            TokenResponse response = webClient.post()
                    .uri(baseUrl + tokenPath)
                    .contentType(MediaType.valueOf("application/json-patch+json"))
                    .bodyValue(new TokenRequest(username, password))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new ApiResponseException("Login worked but no token came back from [" + baseUrl + "]");
            }

            long expiresAt = System.currentTimeMillis() + (response.expiresIn() - 60) * 1000L;
            return new CachedToken(response.accessToken(), expiresAt);

        } catch (WebClientResponseException.Unauthorized e) {
            throw new ApiResponseException("Invalid Username/Password for [" + baseUrl + "]", e);
        } catch (WebClientResponseException e) {
            throw new ApiResponseException("Login at [" + baseUrl + "] answered " + e.getStatusCode().value(), e);
        } catch (WebClientRequestException e) {
            throw new ConnectionFailedException("Cannot reach [" + baseUrl + "]. Check the URL, network or VPN.", e);
        }
    }

    private record CachedToken(String accessToken, long expiresAtMillis) {}

    private record TokenRequest(String user, String pass) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresIn) {}
}