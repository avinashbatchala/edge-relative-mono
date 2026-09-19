package com.edgerelative.broker.groww.http;

import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses the Groww {@code {status, payload, error}} envelope and delegates failures to
 * {@link GrowwErrorDecoder}.
 */
public class GrowwResponseDecoder {

    private final JsonMapper mapper;
    private final GrowwErrorDecoder errorDecoder;

    public GrowwResponseDecoder(JsonMapper mapper, GrowwErrorDecoder errorDecoder) {
        this.mapper = mapper;
        this.errorDecoder = errorDecoder;
    }

    public JsonNode decode(int httpStatus, String retryAfterHeader, String body, GrowwOperation operation, String endpoint) {
        JsonNode root;
        try {
            root = mapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (RuntimeException e) {
            throw new BrokerProtocolException(
                    "Malformed Groww response body", "groww", operation.name(), endpoint, e);
        }

        boolean httpOk = httpStatus >= 200 && httpStatus < 300;
        boolean enveloped = root.has("status");
        String status = enveloped ? root.path("status").asString("SUCCESS") : "SUCCESS";
        if (httpOk && "SUCCESS".equalsIgnoreCase(status)) {
            // Most endpoints use {status, payload}. The token endpoint returns a flat object with no
            // envelope, so fall back to the root node in that case.
            return root.has("payload") ? root.path("payload") : root;
        }

        GrowwApiError error = extractError(root);
        throw errorDecoder.decode(httpStatus, error, retryAfterHeader, operation, endpoint);
    }

    private static GrowwApiError extractError(JsonNode root) {
        JsonNode error = root.path("error");
        if (error.isMissingNode() || error.isNull()) {
            return null;
        }
        String code = error.path("code").isMissingNode() ? null : error.path("code").asString(null);
        String message = error.path("message").isMissingNode() ? null : error.path("message").asString(null);
        return new GrowwApiError(code, message);
    }
}
