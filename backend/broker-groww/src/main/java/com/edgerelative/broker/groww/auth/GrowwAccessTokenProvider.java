package com.edgerelative.broker.groww.auth;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.observability.GrowwMetrics;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Concurrency-safe access-token provider with single-flight refresh.
 *
 * <p>Many virtual-thread callers hitting an expired token must not each generate one. The refresh path
 * is serialized by a dedicated lock; callers that arrive while a refresh is in progress reuse the
 * cached token once it is refreshed. The lock is never held across business HTTP calls. Groww's
 * documented daily generation cap is enforced by the Authentication rate-limit window, not here.
 */
public class GrowwAccessTokenProvider {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final GrowwProperties properties;
    private final GrowwAuthenticationClient authenticationClient;
    private final Clock clock;
    private final GrowwMetrics metrics;
    private final AtomicReference<GrowwAccessToken> cached = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    public GrowwAccessTokenProvider(
            GrowwProperties properties,
            GrowwAuthenticationClient authenticationClient,
            Clock clock,
            GrowwMetrics metrics) {
        this.properties = properties;
        this.authenticationClient = authenticationClient;
        this.clock = clock;
        this.metrics = metrics;
    }

    /** Returns the bearer token, refreshing once (single-flight) when necessary. */
    public String bearerToken() {
        GrowwProperties.Credentials credentials = properties.getCredentials();
        if (properties.resolvedAuthMode() == GrowwProperties.AuthMode.ACCESS_TOKEN) {
            String token = credentials.getAccessToken();
            if (token == null || token.isBlank()) {
                throw new BrokerAuthenticationException(
                        "No Groww credentials are configured; set GROWW_ACCESS_TOKEN, or GROWW_API_KEY and "
                                + "GROWW_API_SECRET",
                        "groww",
                        null,
                        null,
                        null,
                        null);
            }
            return token;
        }
        GrowwAccessToken token = cached.get();
        if (token != null && token.isExpiringAt(clock.instant().plus(REFRESH_MARGIN))) {
            return token.value();
        }
        return refreshSingleFlight().value();
    }

    private GrowwAccessToken refreshSingleFlight() {
        refreshLock.lock();
        try {
            GrowwAccessToken token = cached.get();
            if (token != null && token.isExpiringAt(clock.instant().plus(REFRESH_MARGIN))) {
                return token;
            }
            GrowwAccessToken refreshed = authenticationClient.generateToken();
            cached.set(refreshed);
            metrics.recordAuthRefresh("success");
            return refreshed;
        } catch (RuntimeException e) {
            metrics.recordAuthRefresh("failure");
            throw e;
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Token-refresh recovery hook: invalidate once so the next attempt re-generates.
     *
     * @return false when the mode cannot refresh (a static access token must be operator-provided)
     */
    public boolean recoverFromAuthenticationFailure() {
        if (properties.resolvedAuthMode() == GrowwProperties.AuthMode.ACCESS_TOKEN) {
            return false;
        }
        cached.set(null);
        return true;
    }

    public void invalidate() {
        cached.set(null);
    }
}
