package com.edgerelative.fundamentals.nse.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized fundamental-provider configuration.
 *
 * <p>Free-first default: Yahoo Finance quote-summary keyed by {@code .NS}/{@code .BO}. The API key
 * is reserved for a future paid vendor behind the same port and is empty for Yahoo.
 */
@ConfigurationProperties(prefix = "fundamentals")
public class YahooFinanceProperties {

    private boolean enabled = true;
    private String provider = "yahoo-nse";
    private String apiKey = "";
    private String queryBaseUrl = "https://query1.finance.yahoo.com";
    private String fallbackQueryBaseUrl = "https://query2.finance.yahoo.com";
    private String cookieBaseUrl = "https://fc.yahoo.com";
    private String crumbPath = "/v1/test/getcrumb";
    private String quoteSummaryPath = "/v10/finance/quoteSummary/";
    private String modules =
            "financialData,defaultKeyStatistics,summaryDetail,incomeStatementHistory";
    private String suffixNse = ".NS";
    private String suffixBse = ".BO";
    private Duration requestTimeout = Duration.ofSeconds(30);
    /** Browser User-Agent; Yahoo heavily rate-limits default HTTP-client user agents. */
    private String userAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    /** How long a fetched crumb + cookie session is reused before refreshing. */
    private Duration crumbTtl = Duration.ofMinutes(30);
    /** Minimum spacing between outbound Yahoo requests (politeness / anti-throttle). */
    private Duration minRequestInterval = Duration.ofSeconds(1);

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

    public String getQueryBaseUrl() {
        return queryBaseUrl;
    }

    public void setQueryBaseUrl(String queryBaseUrl) {
        this.queryBaseUrl = queryBaseUrl;
    }

    public String getCookieBaseUrl() {
        return cookieBaseUrl;
    }

    public void setCookieBaseUrl(String cookieBaseUrl) {
        this.cookieBaseUrl = cookieBaseUrl;
    }

    public String getCrumbPath() {
        return crumbPath;
    }

    public void setCrumbPath(String crumbPath) {
        this.crumbPath = crumbPath;
    }

    public String getQuoteSummaryPath() {
        return quoteSummaryPath;
    }

    public void setQuoteSummaryPath(String quoteSummaryPath) {
        this.quoteSummaryPath = quoteSummaryPath;
    }

    public String getModules() {
        return modules;
    }

    public void setModules(String modules) {
        this.modules = modules;
    }

    public String getSuffixNse() {
        return suffixNse;
    }

    public void setSuffixNse(String suffixNse) {
        this.suffixNse = suffixNse;
    }

    public String getSuffixBse() {
        return suffixBse;
    }

    public void setSuffixBse(String suffixBse) {
        this.suffixBse = suffixBse;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getFallbackQueryBaseUrl() {
        return fallbackQueryBaseUrl;
    }

    public void setFallbackQueryBaseUrl(String fallbackQueryBaseUrl) {
        this.fallbackQueryBaseUrl = fallbackQueryBaseUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Duration getCrumbTtl() {
        return crumbTtl;
    }

    public void setCrumbTtl(Duration crumbTtl) {
        this.crumbTtl = crumbTtl;
    }

    public Duration getMinRequestInterval() {
        return minRequestInterval;
    }

    public void setMinRequestInterval(Duration minRequestInterval) {
        this.minRequestInterval = minRequestInterval;
    }

    public void validate() {
        requireText(provider, "fundamentals.provider");
        requireText(queryBaseUrl, "fundamentals.query-base-url");
        requireText(cookieBaseUrl, "fundamentals.cookie-base-url");
        requireText(crumbPath, "fundamentals.crumb-path");
        requireText(quoteSummaryPath, "fundamentals.quote-summary-path");
        requireText(modules, "fundamentals.modules");
        requireText(userAgent, "fundamentals.user-agent");
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalStateException("fundamentals.request-timeout must be positive");
        }
        if (crumbTtl == null || crumbTtl.isZero() || crumbTtl.isNegative()) {
            throw new IllegalStateException("fundamentals.crumb-ttl must be positive");
        }
        if (minRequestInterval == null || minRequestInterval.isNegative()) {
            throw new IllegalStateException("fundamentals.min-request-interval must not be negative");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
    }
}
