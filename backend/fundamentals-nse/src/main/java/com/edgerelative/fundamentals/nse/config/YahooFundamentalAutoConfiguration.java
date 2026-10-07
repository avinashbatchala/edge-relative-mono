package com.edgerelative.fundamentals.nse.config;

import com.edgerelative.fundamentals.api.port.FundamentalProvider;
import com.edgerelative.fundamentals.nse.YahooFundamentalProvider;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the Yahoo NSE/BSE fundamental adapter when {@code fundamentals.provider=yahoo-nse}.
 *
 * <p>A cookie-aware HTTP client is required because Yahoo gates quote-summary behind a cookie and
 * crumb. The client is created here rather than exposed as a shared bean to avoid collisions with
 * other adapters.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fundamentals", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(YahooFinanceProperties.class)
public class YahooFundamentalAutoConfiguration {

    public YahooFundamentalAutoConfiguration(YahooFinanceProperties properties) {
        properties.validate();
    }

    @Bean
    @ConditionalOnMissingBean(FundamentalProvider.class)
    @ConditionalOnProperty(prefix = "fundamentals", name = "provider", havingValue = "yahoo-nse", matchIfMissing = true)
    FundamentalProvider yahooFundamentalProvider(YahooFinanceProperties properties) {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new YahooFundamentalProvider(properties, httpClient, JsonMapper.builder().build());
    }
}
