package com.edgerelative.broker.groww.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized Groww adapter configuration.
 *
 * <p>Rate-limit defaults mirror the current Groww documentation. They are configuration-backed so a
 * documentation change is a configuration change, not a code change. Credentials are never committed;
 * bind them from environment/secret references.
 */
@ConfigurationProperties(prefix = "broker.groww")
public class GrowwProperties {

    private boolean enabled = true;
    private String baseUrl = "https://api.groww.in";
    private String instrumentMasterUrl = "https://growwapi-assets.groww.in/instruments/instrument.csv";
    private String apiVersion = "1.0";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(15);
    private Duration operationTimeout = Duration.ofSeconds(20);
    private int maxInFlight = 64;
    private int bulkMaxConcurrency = 8;

    private final RateLimits rateLimits = new RateLimits();
    private final Retry retry = new Retry();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private final Cooldown cooldown = new Cooldown();
    private final Credentials credentials = new Credentials();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInstrumentMasterUrl() {
        return instrumentMasterUrl;
    }

    public void setInstrumentMasterUrl(String instrumentMasterUrl) {
        this.instrumentMasterUrl = instrumentMasterUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    public void setOperationTimeout(Duration operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    public int getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    public int getBulkMaxConcurrency() {
        return bulkMaxConcurrency;
    }

    public void setBulkMaxConcurrency(int bulkMaxConcurrency) {
        this.bulkMaxConcurrency = bulkMaxConcurrency;
    }

    public RateLimits getRateLimits() {
        return rateLimits;
    }

    public Retry getRetry() {
        return retry;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public Cooldown getCooldown() {
        return cooldown;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    /** Per-category sliding windows. A category is limited by every configured window. */
    public static class RateLimits {
        private List<Window> authentication =
                List.of(Window.of(Duration.ofSeconds(1), 5), Window.of(Duration.ofMinutes(1), 30), Window.of(Duration.ofHours(24), 150));
        private List<Window> orders = List.of(Window.of(Duration.ofSeconds(1), 10), Window.of(Duration.ofMinutes(1), 250));
        private List<Window> liveData = List.of(Window.of(Duration.ofSeconds(1), 10), Window.of(Duration.ofMinutes(1), 300));
        private List<Window> nonTrading = List.of(Window.of(Duration.ofSeconds(1), 20), Window.of(Duration.ofMinutes(1), 500));

        public List<Window> getAuthentication() {
            return authentication;
        }

        public void setAuthentication(List<Window> authentication) {
            this.authentication = authentication;
        }

        public List<Window> getOrders() {
            return orders;
        }

        public void setOrders(List<Window> orders) {
            this.orders = orders;
        }

        public List<Window> getLiveData() {
            return liveData;
        }

        public void setLiveData(List<Window> liveData) {
            this.liveData = liveData;
        }

        public List<Window> getNonTrading() {
            return nonTrading;
        }

        public void setNonTrading(List<Window> nonTrading) {
            this.nonTrading = nonTrading;
        }
    }

    public static class Window {
        private Duration window = Duration.ofSeconds(1);
        private int limit = 1;

        public static Window of(Duration window, int limit) {
            Window w = new Window();
            w.window = window;
            w.limit = limit;
            return w;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(200);
        private double multiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(2);
        private Duration budget = Duration.ofSeconds(20);
        private double jitter = 0.5;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public Duration getBudget() {
            return budget;
        }

        public void setBudget(Duration budget) {
            this.budget = budget;
        }

        public double getJitter() {
            return jitter;
        }

        public void setJitter(double jitter) {
            this.jitter = jitter;
        }
    }

    public static class CircuitBreaker {
        private int failureThreshold = 5;
        private Duration openDuration = Duration.ofSeconds(30);
        private int halfOpenProbes = 1;

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Duration getOpenDuration() {
            return openDuration;
        }

        public void setOpenDuration(Duration openDuration) {
            this.openDuration = openDuration;
        }

        public int getHalfOpenProbes() {
            return halfOpenProbes;
        }

        public void setHalfOpenProbes(int halfOpenProbes) {
            this.halfOpenProbes = halfOpenProbes;
        }
    }

    public static class Cooldown {
        private Duration defaultDuration = Duration.ofSeconds(5);

        public Duration getDefaultDuration() {
            return defaultDuration;
        }

        public void setDefaultDuration(Duration defaultDuration) {
            this.defaultDuration = defaultDuration;
        }
    }

    public static class Credentials {
        private AuthMode mode = AuthMode.AUTO;
        private String accessToken = "";
        private String apiKey = "";
        private String apiSecret = "";
        private String totpCode = "";

        public AuthMode getMode() {
            return mode;
        }

        public void setMode(AuthMode mode) {
            this.mode = mode;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getTotpCode() {
            return totpCode;
        }

        public void setTotpCode(String totpCode) {
            this.totpCode = totpCode;
        }
    }

    /** Authentication flows documented by Groww. {@code AUTO} infers from the credentials present. */
    public enum AuthMode {
        AUTO,
        ACCESS_TOKEN,
        API_KEY_SECRET,
        API_KEY_TOTP
    }

    /**
     * Resolves the effective authentication flow from the credentials actually configured.
     *
     * <p>An explicit mode is honoured when its required credential is present. If it is not (for
     * example a stale {@code GROWW_AUTH_MODE=ACCESS_TOKEN} left from {@code .env.example} while only
     * an API key/secret are supplied), the adapter falls back to whichever credential set is complete.
     * This makes supplying only {@code GROWW_API_KEY} and {@code GROWW_API_SECRET} sufficient.
     */
    public AuthMode resolvedAuthMode() {
        Credentials c = credentials;
        AuthMode requested = c.getMode() == null ? AuthMode.AUTO : c.getMode();

        boolean hasToken = isSet(c.getAccessToken());
        boolean hasKeySecret = isSet(c.getApiKey()) && isSet(c.getApiSecret());
        boolean hasKeyTotp = isSet(c.getApiKey()) && isSet(c.getTotpCode());

        switch (requested) {
            case API_KEY_SECRET -> {
                if (hasKeySecret) {
                    return AuthMode.API_KEY_SECRET;
                }
            }
            case API_KEY_TOTP -> {
                if (hasKeyTotp) {
                    return AuthMode.API_KEY_TOTP;
                }
            }
            case ACCESS_TOKEN -> {
                if (hasToken) {
                    return AuthMode.ACCESS_TOKEN;
                }
            }
            case AUTO -> {
                // fall through to inference
            }
        }

        if (hasKeySecret) {
            return AuthMode.API_KEY_SECRET;
        }
        if (hasKeyTotp) {
            return AuthMode.API_KEY_TOTP;
        }
        if (hasToken) {
            return AuthMode.ACCESS_TOKEN;
        }
        // Nothing configured yet: fail with a clear authentication error at call time.
        return requested == AuthMode.AUTO ? AuthMode.ACCESS_TOKEN : requested;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /** Fails fast on safety-critical misconfiguration. Credentials are validated at call time. */
    public void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("broker.groww.base-url must be configured");
        }
        if (instrumentMasterUrl == null || instrumentMasterUrl.isBlank()) {
            throw new IllegalStateException("broker.groww.instrument-master-url must be configured");
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalStateException("broker.groww.api-version must be configured");
        }
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(requestTimeout, "request-timeout");
        requirePositive(operationTimeout, "operation-timeout");
        if (maxInFlight < 1) {
            throw new IllegalStateException("broker.groww.max-in-flight must be >= 1");
        }
        if (bulkMaxConcurrency < 1) {
            throw new IllegalStateException("broker.groww.bulk-max-concurrency must be >= 1");
        }
        validateWindows("authentication", rateLimits.getAuthentication());
        validateWindows("orders", rateLimits.getOrders());
        validateWindows("live-data", rateLimits.getLiveData());
        validateWindows("non-trading", rateLimits.getNonTrading());
        if (retry.getMaxAttempts() < 1) {
            throw new IllegalStateException("broker.groww.retry.max-attempts must be >= 1");
        }
        if (circuitBreaker.getFailureThreshold() < 1) {
            throw new IllegalStateException("broker.groww.circuit-breaker.failure-threshold must be >= 1");
        }
        if (circuitBreaker.getHalfOpenProbes() < 1) {
            throw new IllegalStateException("broker.groww.circuit-breaker.half-open-probes must be >= 1");
        }
        requirePositive(cooldown.getDefaultDuration(), "cooldown.default-duration");
    }

    private static void validateWindows(String name, List<Window> windows) {
        if (windows == null || windows.isEmpty()) {
            throw new IllegalStateException("broker.groww.rate-limits." + name + " must define at least one window");
        }
        for (Window w : windows) {
            requirePositive(w.getWindow(), "rate-limits." + name + ".window");
            if (w.getLimit() < 1) {
                throw new IllegalStateException("broker.groww.rate-limits." + name + ".limit must be >= 1");
            }
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("broker.groww." + name + " must be positive");
        }
    }
}
