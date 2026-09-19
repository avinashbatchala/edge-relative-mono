package com.edgerelative.broker.groww.http;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerAuthorizationException;
import com.edgerelative.broker.api.error.BrokerException;
import com.edgerelative.broker.api.error.BrokerNotFoundException;
import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.api.error.BrokerTransientException;
import com.edgerelative.broker.api.error.BrokerUnavailableException;
import com.edgerelative.broker.api.error.BrokerUnknownException;
import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.time.Duration;

/**
 * Single place that maps Groww HTTP status + error code to a typed broker failure.
 *
 * <p>Messages are bounded and never include tokens or Authorization headers. Groww codes: GA000
 * internal, GA001 bad request, GA003 unable to serve, GA004 missing entity, GA005 not authorised,
 * GA006 cannot process, GA007 duplicate reference id.
 */
public class GrowwErrorDecoder {

    public BrokerException decode(
            int httpStatus, GrowwApiError error, String retryAfterHeader, GrowwOperation operation, String endpoint) {
        String code = error == null ? null : error.code();
        String message = error == null || error.message() == null ? "Groww request failed" : error.message();

        if (httpStatus == 429) {
            return new BrokerRateLimitException(message, "groww", operation.name(), endpoint, code, 429, parseRetryAfter(retryAfterHeader));
        }
        if (httpStatus == 401 || "GA005".equals(code) && httpStatus == 401) {
            return new BrokerAuthenticationException(message, "groww", operation.name(), endpoint, 401, null);
        }
        if (httpStatus == 403 || httpStatus == 401 || "GA005".equals(code)) {
            return new BrokerAuthorizationException(message, "groww", operation.name(), endpoint, code, httpStatus);
        }
        if (httpStatus == 404 || "GA004".equals(code)) {
            return new BrokerNotFoundException(message, "groww", operation.name(), endpoint, code, httpStatus);
        }
        if (httpStatus == 400 || httpStatus == 422 || "GA001".equals(code) || "GA006".equals(code) || "GA007".equals(code)) {
            return new BrokerValidationException(message, "groww", operation.name(), endpoint, code, httpStatus);
        }
        if (httpStatus >= 500 || "GA000".equals(code)) {
            return new BrokerUnavailableException(message, "groww", operation.name(), endpoint, httpStatus, null);
        }
        if ("GA003".equals(code)) {
            return new BrokerTransientException(message, "groww", operation.name(), endpoint, httpStatus, null);
        }
        return new BrokerUnknownException(message, "groww", operation.name(), endpoint, code, httpStatus, null);
    }

    static Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
