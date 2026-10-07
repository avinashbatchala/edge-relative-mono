package com.edgerelative.llm.deepseek.config;

import com.edgerelative.llm.api.port.LlmClient;
import com.edgerelative.llm.deepseek.DeepSeekLlmClient;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the DeepSeek adapter when {@code llm.provider=deepseek}. Registered as a Spring Boot
 * auto-configuration so the application depends on this module without constructing provider beans.
 *
 * <p>An empty API key does not fail startup: {@code LlmClient.available()} reports false and the
 * advisory endpoints degrade rather than block the trading application.
 *
 * <p>The client is constructed with its own HTTP client and JSON mapper so this module exposes no
 * generic {@code HttpClient}/{@code JsonMapper} bean that could collide with another adapter.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "llm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekAutoConfiguration {

    public DeepSeekAutoConfiguration(DeepSeekProperties properties) {
        properties.validate();
    }

    @Bean
    @ConditionalOnMissingBean(LlmClient.class)
    @ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "deepseek", matchIfMissing = true)
    LlmClient deepSeekLlmClient(DeepSeekProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new DeepSeekLlmClient(properties, httpClient, JsonMapper.builder().build());
    }
}
