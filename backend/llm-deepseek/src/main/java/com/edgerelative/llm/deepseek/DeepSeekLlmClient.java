package com.edgerelative.llm.deepseek;

import com.edgerelative.llm.api.error.LlmAuthenticationException;
import com.edgerelative.llm.api.error.LlmException;
import com.edgerelative.llm.api.error.LlmProtocolException;
import com.edgerelative.llm.api.error.LlmRateLimitException;
import com.edgerelative.llm.api.error.LlmTimeoutException;
import com.edgerelative.llm.api.error.LlmUnavailableException;
import com.edgerelative.llm.api.model.LlmCompletion;
import com.edgerelative.llm.api.model.LlmPrompt;
import com.edgerelative.llm.api.port.LlmClient;
import com.edgerelative.llm.deepseek.config.DeepSeekProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DeepSeek chat-completions adapter (OpenAI-compatible wire format).
 *
 * <p>Blocking, one attempt per call; intended to run on a virtual thread. It sends only the prompt
 * text, never credentials beyond the bearer key, and never reads account or order state.
 */
public class DeepSeekLlmClient implements LlmClient {

    static final String PROVIDER = "deepseek";

    private final DeepSeekProperties properties;
    private final HttpClient httpClient;
    private final JsonMapper mapper;

    public DeepSeekLlmClient(DeepSeekProperties properties, HttpClient httpClient, JsonMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public boolean available() {
        return properties.isEnabled() && isSet(properties.getApiKey());
    }

    @Override
    public LlmCompletion complete(LlmPrompt prompt) {
        if (!available()) {
            throw new LlmAuthenticationException(
                    "DeepSeek is not configured", PROVIDER, "complete", path(), null);
        }
        HttpRequest request = build(prompt);
        HttpResponse<String> response = send(request);
        return decode(response);
    }

    private HttpRequest build(LlmPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getDeepseek().getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt.system()),
                Map.of("role", "user", "content", prompt.user())));
        body.put("temperature", prompt.temperature());
        body.put("max_tokens", prompt.maxOutputTokens());
        body.put("stream", false);
        return HttpRequest.newBuilder(URI.create(properties.getDeepseek().getBaseUrl() + path()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .timeout(properties.getRequestTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmTimeoutException("DeepSeek request timed out", PROVIDER, "complete", path(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(
                    "DeepSeek request interrupted", PROVIDER, "complete", path(), null, e);
        } catch (IOException e) {
            throw new LlmUnavailableException(
                    "DeepSeek request failed: " + describe(e), PROVIDER, "complete", path(), null, e);
        }
    }

    private LlmCompletion decode(HttpResponse<String> response) {
        int status = response.statusCode();
        String text = response.body() == null ? "" : response.body();
        if (status < 200 || status >= 300) {
            throw mapError(status, text);
        }
        JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (RuntimeException e) {
            throw new LlmProtocolException("Malformed DeepSeek response", PROVIDER, "complete", path(), e);
        }
        String content = root.path("choices").path(0).path("message").path("content").asString(null);
        if (content == null) {
            throw new LlmProtocolException("DeepSeek response missing completion text", PROVIDER, "complete", path(), null);
        }
        String finishReason = root.path("choices").path(0).path("finish_reason").asString(null);
        JsonNode usage = root.path("usage");
        return new LlmCompletion(
                PROVIDER,
                properties.getDeepseek().getModel(),
                content,
                finishReason,
                intOrNull(usage, "prompt_tokens"),
                intOrNull(usage, "completion_tokens"));
    }

    private LlmException mapError(int status, String body) {
        String providerMessage = providerMessage(body);
        if (status == 401 || status == 403) {
            return new LlmAuthenticationException(
                    "DeepSeek rejected the request: " + providerMessage, PROVIDER, "complete", path(), null);
        }
        if (status == 429) {
            return new LlmRateLimitException(
                    "DeepSeek rate limited the request: " + providerMessage, PROVIDER, "complete", path(), null);
        }
        if (status == 408) {
            return new LlmTimeoutException(
                    "DeepSeek timed out: " + providerMessage, PROVIDER, "complete", path(), null);
        }
        if (status >= 500) {
            return new LlmUnavailableException(
                    "DeepSeek is unavailable: " + providerMessage, PROVIDER, "complete", path(), status, null);
        }
        return new LlmException(
                "DeepSeek request failed: " + providerMessage, PROVIDER, "complete", path(), null, status, false, null);
    }

    private String providerMessage(String body) {
        if (body == null || body.isBlank()) {
            return "no provider detail";
        }
        try {
            String message = mapper.readTree(body).path("error").path("message").asString(null);
            return message == null ? "HTTP body not in error format" : message;
        } catch (RuntimeException e) {
            return "HTTP body not in error format";
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private String path() {
        return properties.getDeepseek().getPath();
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String describe(IOException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + message;
    }
}
