package com.edgerelative.broker.groww.auth;

import java.time.Instant;

/**
 * Cached access token. Never logged.
 */
public record GrowwAccessToken(String value, Instant expiresAt, String tokenRefId) {

    public boolean isExpiringAt(Instant threshold) {
        return expiresAt != null && expiresAt.isAfter(threshold);
    }

    @Override
    public String toString() {
        return "GrowwAccessToken[redacted, expiresAt=" + expiresAt + "]";
    }
}
