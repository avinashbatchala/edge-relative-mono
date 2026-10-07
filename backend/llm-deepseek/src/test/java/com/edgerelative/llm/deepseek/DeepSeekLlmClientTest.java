package com.edgerelative.llm.deepseek;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.llm.api.error.LlmAuthenticationException;
import com.edgerelative.llm.api.error.LlmProtocolException;
import com.edgerelative.llm.api.error.LlmRateLimitException;
import com.edgerelative.llm.api.model.LlmCompletion;
import com.edgerelative.llm.api.model.LlmPrompt;
import com.edgerelative.llm.deepseek.config.DeepSeekProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import java.net.http.HttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeepSeekLlmClientTest {

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private DeepSeekLlmClient client(String apiKey) {
        DeepSeekProperties properties = new DeepSeekProperties();
        properties.setApiKey(apiKey);
        properties.getDeepseek().setBaseUrl(server.baseUrl());
        return new DeepSeekLlmClient(
                properties, HttpClient.newHttpClient(), JsonMapper.builder().build());
    }

    @Test
    void returnsNarratedCompletion() {
        server.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "choices": [{"message": {"content": "Revenue grew."}, "finish_reason": "stop"}],
                          "usage": {"prompt_tokens": 12, "completion_tokens": 4}
                        }
                        """)));

        LlmCompletion completion = client("test-key").complete(LlmPrompt.of("be factual", "revenue=100"));

        assertThat(completion.provider()).isEqualTo("deepseek");
        assertThat(completion.text()).isEqualTo("Revenue grew.");
        assertThat(completion.promptTokens()).isEqualTo(12);
        assertThat(completion.completionTokens()).isEqualTo(4);
    }

    @Test
    void mapsUnauthorizedToAuthenticationFailure() {
        server.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"invalid key\"}}")));

        assertThatThrownBy(() -> client("bad").complete(LlmPrompt.of("s", "u")))
                .isInstanceOf(LlmAuthenticationException.class);
    }

    @Test
    void mapsTooManyRequestsToRateLimit() {
        server.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(429)
                .withBody("{\"error\":{\"message\":\"slow down\"}}")));

        assertThatThrownBy(() -> client("k").complete(LlmPrompt.of("s", "u")))
                .isInstanceOf(LlmRateLimitException.class);
    }

    @Test
    void rejectsMalformedSuccessBody() {
        server.stubFor(post(urlEqualTo("/chat/completions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"choices\":[]}")));

        assertThatThrownBy(() -> client("k").complete(LlmPrompt.of("s", "u")))
                .isInstanceOf(LlmProtocolException.class);
    }

    @Test
    void reportsUnavailableWhenKeyMissing() {
        assertThat(client("").available()).isFalse();
        assertThatThrownBy(() -> client("").complete(LlmPrompt.of("s", "u")))
                .isInstanceOf(LlmAuthenticationException.class);
    }
}
