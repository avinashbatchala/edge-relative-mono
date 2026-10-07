package com.edgerelative.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Proves a Spring-imported {@code secrets.properties} populates the {@code GROWW_*} keys used by the
 * placeholders in {@code application.yaml}, so the key/secret flow works without shell exports.
 */
class SecretsPropertiesImportTest {

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }

    @Test
    void importedSecretsPropertiesPopulateGrowwPlaceholders() throws Exception {
        Path secrets = Files.createTempFile("edge-relative-secrets-", ".properties");
        Files.writeString(secrets, """
                GROWW_AUTH_MODE=API_KEY_SECRET
                GROWW_API_KEY=key-from-secrets
                GROWW_API_SECRET=secret-from-secrets
                """);
        try {
            SpringApplication application = new SpringApplication(EmptyConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setDefaultProperties(Map.of(
                    "spring.config.name", "edge-relative-secrets-test",
                    "spring.config.import", "optional:file:" + secrets.toAbsolutePath(),
                    // Mirrors the indirection application.yaml uses.
                    "probe.api-key", "${GROWW_API_KEY:absent}",
                    "probe.auth-mode", "${GROWW_AUTH_MODE:AUTO}"));

            try (ConfigurableApplicationContext context = application.run()) {
                var environment = context.getEnvironment();
                assertThat(environment.getProperty("GROWW_API_KEY")).isEqualTo("key-from-secrets");
                assertThat(environment.getProperty("GROWW_API_SECRET")).isEqualTo("secret-from-secrets");
                assertThat(environment.getProperty("probe.api-key")).isEqualTo("key-from-secrets");
                assertThat(environment.getProperty("probe.auth-mode")).isEqualTo("API_KEY_SECRET");
            }
        } finally {
            Files.deleteIfExists(secrets);
        }
    }

    /**
     * The LLM and fundamentals placeholders in {@code application.yaml} resolve from the same file,
     * so the backend holds the only copy of the DeepSeek key (ADR-007).
     */
    @Test
    void importedSecretsPropertiesPopulateLlmAndFundamentalPlaceholders() throws Exception {
        Path secrets = Files.createTempFile("edge-relative-secrets-llm-", ".properties");
        Files.writeString(secrets, """
                LLM_PROVIDER=deepseek
                LLM_API_KEY=llm-key-from-secrets
                DEEPSEEK_MODEL=deepseek-chat
                FUNDAMENTALS_PROVIDER=yahoo-nse
                """);
        try {
            SpringApplication application = new SpringApplication(EmptyConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setDefaultProperties(Map.of(
                    "spring.config.name", "edge-relative-secrets-llm-test",
                    "spring.config.import", "optional:file:" + secrets.toAbsolutePath(),
                    "probe.llm-key", "${LLM_API_KEY:absent}",
                    "probe.llm-provider", "${LLM_PROVIDER:deepseek}",
                    "probe.fundamentals-provider", "${FUNDAMENTALS_PROVIDER:yahoo-nse}"));

            try (ConfigurableApplicationContext context = application.run()) {
                var environment = context.getEnvironment();
                assertThat(environment.getProperty("LLM_API_KEY")).isEqualTo("llm-key-from-secrets");
                assertThat(environment.getProperty("probe.llm-key")).isEqualTo("llm-key-from-secrets");
                assertThat(environment.getProperty("probe.llm-provider")).isEqualTo("deepseek");
                assertThat(environment.getProperty("probe.fundamentals-provider")).isEqualTo("yahoo-nse");
            }
        } finally {
            Files.deleteIfExists(secrets);
        }
    }
}
