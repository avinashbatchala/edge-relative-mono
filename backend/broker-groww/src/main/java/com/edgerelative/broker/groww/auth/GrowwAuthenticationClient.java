package com.edgerelative.broker.groww.auth;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.dto.request.GrowwTokenApprovalRequest;
import com.edgerelative.broker.groww.dto.request.GrowwTokenTotpRequest;
import com.edgerelative.broker.groww.http.GrowwHttpClient;
import com.edgerelative.broker.groww.http.GrowwRequestFactory;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import com.edgerelative.broker.groww.resilience.GrowwCallPriority;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generates Groww access tokens for the API-key flows.
 *
 * <p>The checksum is SHA-256(secret + epochSeconds), matching the Groww documentation, with the clock
 * injected for deterministic tests. The call is routed through the shared executor so it consumes the
 * Authentication quota and never bypasses rate limiting.
 */
public class GrowwAuthenticationClient {

    private static final String SHA_256 = "SHA-256";

    private final GrowwProperties properties;
    private final GrowwCallExecutor callExecutor;
    private final GrowwHttpClient httpClient;
    private final GrowwRequestFactory requestFactory;
    private final JsonMapper mapper;
    private final Clock clock;

    public GrowwAuthenticationClient(
            GrowwProperties properties,
            GrowwCallExecutor callExecutor,
            GrowwHttpClient httpClient,
            GrowwRequestFactory requestFactory,
            JsonMapper mapper,
            Clock clock) {
        this.properties = properties;
        this.callExecutor = callExecutor;
        this.httpClient = httpClient;
        this.requestFactory = requestFactory;
        this.mapper = mapper;
        this.clock = clock;
    }

    public GrowwAccessToken generateToken() {
        GrowwProperties.Credentials credentials = properties.getCredentials();
        String apiKey = credentials.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BrokerAuthenticationException(
                    "Groww API key is not configured", "groww", GrowwOperation.TOKEN_GENERATE.name(), null, null, null);
        }
        Object body = switch (properties.resolvedAuthMode()) {
            case API_KEY_SECRET -> approvalBody(credentials);
            case API_KEY_TOTP -> totpBody(credentials);
            case ACCESS_TOKEN, AUTO -> throw new BrokerAuthenticationException(
                    "Token generation is not used in " + properties.resolvedAuthMode() + " mode",
                    "groww",
                    GrowwOperation.TOKEN_GENERATE.name(),
                    null,
                    null,
                    null);
        };
        JsonNode payload = callExecutor.execute(
                GrowwOperation.TOKEN_GENERATE,
                GrowwCallPriority.INTERACTIVE,
                () -> httpClient.exchange(requestFactory.authRequest(body, apiKey), GrowwOperation.TOKEN_GENERATE));
        GrowwTokenResponse response = mapper.treeToValue(payload, GrowwTokenResponse.class);
        if (response == null || response.token() == null || response.token().isBlank()) {
            throw new BrokerAuthenticationException(
                    "Groww token response did not contain a token",
                    "groww",
                    GrowwOperation.TOKEN_GENERATE.name(),
                    null,
                    null,
                    null);
        }
        return new GrowwAccessToken(response.token(), parseExpiry(response.expiry()), response.tokenRefId());
    }

    private GrowwTokenApprovalRequest approvalBody(GrowwProperties.Credentials credentials) {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        return new GrowwTokenApprovalRequest(
                "approval", checksum(credentials.getApiSecret(), timestamp), timestamp);
    }

    private GrowwTokenTotpRequest totpBody(GrowwProperties.Credentials credentials) {
        String totp = credentials.getTotpCode();
        if (totp == null || totp.isBlank()) {
            throw new BrokerAuthenticationException(
                    "Groww TOTP code is not configured", "groww", GrowwOperation.TOKEN_GENERATE.name(), null, null, null);
        }
        return new GrowwTokenTotpRequest("totp", totp);
    }

    private java.time.Instant parseExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return clock.instant().plus(java.time.Duration.ofHours(12));
        }
        try {
            return java.time.LocalDateTime.parse(expiry).atZone(java.time.ZoneOffset.UTC).toInstant();
        } catch (RuntimeException _) {
            return clock.instant().plus(java.time.Duration.ofHours(12));
        }
    }

    static String checksum(String secret, String timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest((secret + timestamp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
