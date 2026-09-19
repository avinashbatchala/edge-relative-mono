package com.edgerelative.broker.groww.client;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.groww.config.GrowwProperties;
import com.edgerelative.broker.groww.support.GrowwTestFixture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the API-key/secret approval flow: no access token is supplied, and a
 * read-only call must first generate a token from the key/secret and then use it.
 */
class GrowwApprovalAuthIntegrationTest {

    private static final String TOKEN_RESPONSE = """
            {"token":"generated-token","tokenRefId":"ref-1","sessionName":"s",
             "expiry":"2030-01-01T00:00:00","isActive":true}""";

    private static final String QUOTE_RESPONSE = """
            {"status":"SUCCESS","payload":{"last_price":2500.55}}""";

    @Test
    void apiKeySecretWithAutoModeGeneratesAndUsesToken() {
        assertApprovalFlow(properties -> properties.getCredentials().setMode(GrowwProperties.AuthMode.AUTO));
    }

    @Test
    void staleAccessTokenModeFallsBackToApiKeySecret() {
        assertApprovalFlow(properties -> properties.getCredentials().setMode(GrowwProperties.AuthMode.ACCESS_TOKEN));
    }

    private void assertApprovalFlow(Consumer<GrowwProperties> mode) {
        try (GrowwTestFixture fixture = new GrowwTestFixture(properties -> {
            mode.accept(properties);
            properties.getCredentials().setAccessToken("");
            properties.getCredentials().setApiKey("my-key");
            properties.getCredentials().setApiSecret("my-secret");
        })) {
            fixture.server()
                    .stubFor(post(urlPathEqualTo("/v1/token/api/access")).willReturn(okJson(TOKEN_RESPONSE)));
            fixture.server()
                    .stubFor(get(urlPathEqualTo("/v1/live-data/quote")).willReturn(okJson(QUOTE_RESPONSE)));

            var quote = fixture.marketData().quote(BrokerExchange.NSE, BrokerSegment.CASH, "RELIANCE");
            assertThat(quote.lastPrice()).isEqualByComparingTo("2500.55");

            fixture.server()
                    .verify(postRequestedFor(urlPathEqualTo("/v1/token/api/access"))
                            .withHeader("Authorization", equalTo("Bearer my-key"))
                            .withRequestBody(containing("\"key_type\":\"approval\""))
                            .withRequestBody(containing("\"checksum\""))
                            .withRequestBody(containing("\"timestamp\"")));
            fixture.server()
                    .verify(getRequestedFor(urlPathEqualTo("/v1/live-data/quote"))
                            .withHeader("Authorization", equalTo("Bearer generated-token")));
        }
    }
}
