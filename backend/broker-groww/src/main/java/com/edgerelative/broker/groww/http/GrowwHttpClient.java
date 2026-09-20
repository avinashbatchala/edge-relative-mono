package com.edgerelative.broker.groww.http;

import com.edgerelative.broker.api.error.BrokerInterruptedException;
import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.error.BrokerTimeoutException;
import com.edgerelative.broker.api.error.BrokerUnavailableException;
import com.edgerelative.broker.groww.resilience.GrowwOperation;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import tools.jackson.databind.JsonNode;

/**
 * Executes one blocking HTTP call and decodes the Groww envelope.
 *
 * <p>Intentionally blocking: it is designed to run on a virtual thread. It performs exactly one
 * network attempt; retries, rate limiting and cooldowns are the executor's responsibility.
 */
public class GrowwHttpClient {

    private final HttpClient client;
    private final GrowwResponseDecoder decoder;

    public GrowwHttpClient(HttpClient client, GrowwResponseDecoder decoder) {
        this.client = client;
        this.decoder = decoder;
    }

    /**
     * Sends a request and returns the {@code payload} node, mapping failures to typed exceptions.
     */
    public JsonNode exchange(HttpRequest request, GrowwOperation operation) {
        HttpResponse<String> response = send(request, operation);
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        return decoder.decode(
                response.statusCode(), retryAfter, response.body(), operation, request.uri().getPath());
    }

    /**
     * For non-JSON responses such as the instrument CSV.
     */
    public String getText(HttpRequest request, GrowwOperation operation) {
        HttpResponse<String> response = send(request, operation);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            decoder.decode(response.statusCode(), retryAfter, response.body(), operation, request.uri().getPath());
        }
        return response.body();
    }

    private HttpResponse<String> send(HttpRequest request, GrowwOperation operation) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new BrokerTimeoutException(
                    "Groww request timed out", "groww", operation.name(), request.uri().getPath(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerInterruptedException(
                    "Groww request interrupted", "groww", operation.name(), request.uri().getPath(), e);
        } catch (IOException e) {
            throw new BrokerUnavailableException(
                    "Groww request failed: " + describe(e),
                    "groww",
                    operation.name(),
                    request.uri().getPath(),
                    null,
                    e);
        } catch (RuntimeException e) {
            throw new BrokerProtocolException(
                    "Groww response could not be processed", "groww", operation.name(), request.uri().getPath(), e);
        }
    }

    /** Surface the transport root cause so {@code last_error} is actionable (token rotation, TLS, reset). */
    private static String describe(IOException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + message;
    }
}
