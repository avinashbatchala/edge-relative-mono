package com.edgerelative.llm.deepseek.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized LLM configuration.
 *
 * <p>Mirrors {@code GrowwProperties}: credentials are never committed and are bound from the
 * environment or a secret reference. The application boots with an empty key; LLM features report
 * unavailable instead of failing startup.
 *
 * <p>The {@code llm} root prefix also carries the provider selector. When a second provider adapter
 * is added, the root keys move to a shared configuration class so both adapters bind one source.
 */
@ConfigurationProperties(prefix = "llm")
public class DeepSeekProperties {

    private boolean enabled = true;
    private String provider = "deepseek";
    private String apiKey = "";
    private Duration requestTimeout = Duration.ofSeconds(30);
    private final DeepSeek deepseek = new DeepSeek();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public DeepSeek getDeepseek() {
        return deepseek;
    }

    public void validate() {
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("llm.provider must be configured");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalStateException("llm.request-timeout must be positive");
        }
        requireText(deepseek.getBaseUrl(), "llm.deepseek.base-url");
        requireText(deepseek.getModel(), "llm.deepseek.model");
        requireText(deepseek.getPath(), "llm.deepseek.path");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
    }

    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
        private String path = "/chat/completions";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
