package com.edgerelative.broker.groww.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import com.edgerelative.broker.groww.support.GrowwPropertiesBuilder;
import com.edgerelative.broker.groww.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GrowwAccessTokenProviderSingleFlightTest {

    @Test
    void concurrentCallersShareOneRefresh() throws Exception {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.getCredentials().setMode(GrowwProperties.AuthMode.API_KEY_SECRET);
        properties.getCredentials().setApiKey("key");
        properties.getCredentials().setApiSecret("secret");

        GrowwAuthenticationClient authenticationClient = mock(GrowwAuthenticationClient.class);
        when(authenticationClient.generateToken())
                .thenReturn(new GrowwAccessToken("generated", Instant.parse("2030-01-01T00:00:00Z"), "ref"));

        GrowwAccessTokenProvider provider = new GrowwAccessTokenProvider(
                properties,
                authenticationClient,
                new MutableClock(Instant.parse("2025-01-01T00:00:00Z")),
                new GrowwMetrics(new SimpleMeterRegistry()));

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<String>> tasks = java.util.Collections.nCopies(64, provider::bearerToken);
            var futures = pool.invokeAll(tasks);
            for (var future : futures) {
                assertThat(future.get()).isEqualTo("generated");
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        verify(authenticationClient, times(1)).generateToken();
    }

    @Test
    void staticAccessTokenModeNeverGenerates() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.getCredentials().setMode(GrowwProperties.AuthMode.ACCESS_TOKEN);
        properties.getCredentials().setAccessToken("static-token");

        GrowwAuthenticationClient authenticationClient = mock(GrowwAuthenticationClient.class);
        GrowwAccessTokenProvider provider = new GrowwAccessTokenProvider(
                properties,
                authenticationClient,
                new MutableClock(Instant.parse("2025-01-01T00:00:00Z")),
                new GrowwMetrics(new SimpleMeterRegistry()));

        assertThat(provider.bearerToken()).isEqualTo("static-token");
        assertThat(provider.recoverFromAuthenticationFailure()).isFalse();
    }

    @Test
    void recoveringInvalidatesSoNextCallRefreshes() {
        GrowwProperties properties = GrowwPropertiesBuilder.defaults();
        properties.getCredentials().setMode(GrowwProperties.AuthMode.API_KEY_SECRET);
        properties.getCredentials().setAccessToken("");
        properties.getCredentials().setApiKey("key");
        properties.getCredentials().setApiSecret("secret");

        GrowwAuthenticationClient authenticationClient = mock(GrowwAuthenticationClient.class);
        when(authenticationClient.generateToken())
                .thenReturn(new GrowwAccessToken("first", Instant.parse("2030-01-01T00:00:00Z"), "r1"))
                .thenReturn(new GrowwAccessToken("second", Instant.parse("2030-01-01T00:00:00Z"), "r2"));

        GrowwAccessTokenProvider provider = new GrowwAccessTokenProvider(
                properties,
                authenticationClient,
                new MutableClock(Instant.parse("2025-01-01T00:00:00Z")),
                new GrowwMetrics(new SimpleMeterRegistry()));

        assertThat(provider.bearerToken()).isEqualTo("first");
        assertThat(provider.recoverFromAuthenticationFailure()).isTrue();
        assertThat(provider.bearerToken()).isEqualTo("second");
    }
}
