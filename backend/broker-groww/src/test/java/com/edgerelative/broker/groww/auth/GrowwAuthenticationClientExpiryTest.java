package com.edgerelative.broker.groww.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Groww's zone-less expiry must be read in IST or proactive refresh misses the daily rotation. */
class GrowwAuthenticationClientExpiryTest {

    private static final Instant FALLBACK = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void zoneLessExpiryIsInterpretedInIst() {
        assertThat(GrowwAuthenticationClient.parseExpiry("2026-09-21T06:00:00", FALLBACK))
                .isEqualTo(Instant.parse("2026-09-21T00:30:00Z"));
    }

    @Test
    void spaceSeparatedExpiryIsAccepted() {
        assertThat(GrowwAuthenticationClient.parseExpiry("2026-09-21 06:00:00", FALLBACK))
                .isEqualTo(Instant.parse("2026-09-21T00:30:00Z"));
    }

    @Test
    void explicitOffsetIsHonoured() {
        assertThat(GrowwAuthenticationClient.parseExpiry("2026-09-21T06:00:00Z", FALLBACK))
                .isEqualTo(Instant.parse("2026-09-21T06:00:00Z"));
    }

    @Test
    void unparsableFallsBack() {
        assertThat(GrowwAuthenticationClient.parseExpiry("not-a-date", FALLBACK)).isEqualTo(FALLBACK);
        assertThat(GrowwAuthenticationClient.parseExpiry("", FALLBACK)).isEqualTo(FALLBACK);
        assertThat(GrowwAuthenticationClient.parseExpiry(null, FALLBACK)).isEqualTo(FALLBACK);
    }
}
