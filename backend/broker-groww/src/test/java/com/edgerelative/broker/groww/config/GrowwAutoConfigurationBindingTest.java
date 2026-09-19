package com.edgerelative.broker.groww.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.groww.auth.GrowwAccessTokenProvider;
import com.edgerelative.broker.groww.resilience.GrowwCallExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the adapter starts safely with no credentials and binds API key/secret when provided.
 * No real secret is required for the application to boot.
 */
class GrowwAutoConfigurationBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GrowwAutoConfiguration.class));

    @Test
    void startsWithoutCredentialsAndKeepsDefaultsNonNull() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GrowwProperties.class);
            GrowwProperties properties = context.getBean(GrowwProperties.class);
            assertThat(properties.getCredentials().getMode()).isEqualTo(GrowwProperties.AuthMode.AUTO);
            assertThat(properties.getCredentials().getAccessToken()).isEmpty();
            assertThat(properties.getCredentials().getApiKey()).isEmpty();
            assertThat(properties.getCredentials().getApiSecret()).isEmpty();
            assertThat(properties.getCredentials().getTotpCode()).isEmpty();
            assertThat(context).hasSingleBean(GrowwAccessTokenProvider.class);
            assertThat(context).hasSingleBean(GrowwCallExecutor.class);
        });
    }

    @Test
    void bindsApiKeyAndSecretForTheApprovalFlow() {
        runner.withPropertyValues(
                        "broker.groww.credentials.mode=API_KEY_SECRET",
                        "broker.groww.credentials.api-key=my-key",
                        "broker.groww.credentials.api-secret=my-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    GrowwProperties properties = context.getBean(GrowwProperties.class);
                    assertThat(properties.getCredentials().getMode())
                            .isEqualTo(GrowwProperties.AuthMode.API_KEY_SECRET);
                    assertThat(properties.getCredentials().getApiKey()).isEqualTo("my-key");
                    assertThat(properties.getCredentials().getApiSecret()).isEqualTo("my-secret");
                    assertThat(properties.resolvedAuthMode()).isEqualTo(GrowwProperties.AuthMode.API_KEY_SECRET);
                });
    }

    @Test
    void autoDetectsApiKeySecretWhenModeIsUnset() {
        runner.withPropertyValues(
                        "broker.groww.credentials.api-key=my-key",
                        "broker.groww.credentials.api-secret=my-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GrowwProperties.class).resolvedAuthMode())
                            .isEqualTo(GrowwProperties.AuthMode.API_KEY_SECRET);
                });
    }

    @Test
    void staleAccessTokenModeFallsBackWhenOnlyApiKeySecretAreProvided() {
        runner.withPropertyValues(
                        "broker.groww.credentials.mode=ACCESS_TOKEN",
                        "broker.groww.credentials.api-key=my-key",
                        "broker.groww.credentials.api-secret=my-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GrowwProperties.class).resolvedAuthMode())
                            .isEqualTo(GrowwProperties.AuthMode.API_KEY_SECRET);
                });
    }

    @Test
    void autoDetectsAccessTokenWhenOnlyTokenIsProvided() {
        runner.withPropertyValues("broker.groww.credentials.access-token=token-value")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GrowwProperties.class).resolvedAuthMode())
                            .isEqualTo(GrowwProperties.AuthMode.ACCESS_TOKEN);
                });
    }

    @Test
    void bindsStaticAccessToken() {
        runner.withPropertyValues(
                        "broker.groww.credentials.mode=ACCESS_TOKEN",
                        "broker.groww.credentials.access-token=token-value")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GrowwProperties.class).getCredentials().getAccessToken())
                            .isEqualTo("token-value");
                });
    }

    @Test
    void failsFastOnInvalidSafetyCriticalConfiguration() {
        runner.withPropertyValues("broker.groww.base-url=").run(context -> assertThat(context).hasFailed());
    }
}
