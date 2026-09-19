package com.edgerelative.application.broker;

import com.edgerelative.broker.api.error.BrokerAuthenticationException;
import com.edgerelative.broker.api.error.BrokerAuthorizationException;
import com.edgerelative.broker.api.error.BrokerException;
import com.edgerelative.broker.api.error.BrokerNotFoundException;
import com.edgerelative.broker.api.error.BrokerOperationNotEnabledException;
import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.error.BrokerRateLimitException;
import com.edgerelative.broker.api.error.BrokerTimeoutException;
import com.edgerelative.broker.api.error.BrokerUnavailableException;
import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.application.broker.api.ApiError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps broker failures to stable application responses. Messages are safe; tokens and authorization
 * headers are never present in {@link BrokerException}.
 */
@RestControllerAdvice
public class BrokerExceptionHandler {

    @ExceptionHandler(BrokerOperationNotEnabledException.class)
    public ResponseEntity<ApiError> operationNotEnabled(BrokerOperationNotEnabledException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiError(BrokerOperationNotEnabledException.CODE, e.getMessage(), e.operation()));
    }

    @ExceptionHandler(BrokerValidationException.class)
    public ResponseEntity<ApiError> validation(BrokerValidationException e) {
        return respond(HttpStatus.BAD_REQUEST, "BROKER_VALIDATION_FAILED", e);
    }

    @ExceptionHandler(BrokerNotFoundException.class)
    public ResponseEntity<ApiError> notFound(BrokerNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "BROKER_NOT_FOUND", e);
    }

    @ExceptionHandler(BrokerAuthenticationException.class)
    public ResponseEntity<ApiError> authentication(BrokerAuthenticationException e) {
        return respond(HttpStatus.UNAUTHORIZED, "BROKER_AUTHENTICATION_FAILED", e);
    }

    @ExceptionHandler(BrokerAuthorizationException.class)
    public ResponseEntity<ApiError> authorization(BrokerAuthorizationException e) {
        return respond(HttpStatus.FORBIDDEN, "BROKER_AUTHORIZATION_FAILED", e);
    }

    @ExceptionHandler(BrokerRateLimitException.class)
    public ResponseEntity<ApiError> rateLimited(BrokerRateLimitException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (e.retryAfter() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfter().toSeconds()));
        }
        return builder.body(new ApiError("BROKER_RATE_LIMITED", e.getMessage(), e.operation()));
    }

    @ExceptionHandler(BrokerTimeoutException.class)
    public ResponseEntity<ApiError> timeout(BrokerTimeoutException e) {
        return respond(HttpStatus.GATEWAY_TIMEOUT, "BROKER_TIMEOUT", e);
    }

    @ExceptionHandler(BrokerUnavailableException.class)
    public ResponseEntity<ApiError> unavailable(BrokerUnavailableException e) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "BROKER_UNAVAILABLE", e);
    }

    @ExceptionHandler(BrokerProtocolException.class)
    public ResponseEntity<ApiError> protocol(BrokerProtocolException e) {
        return respond(HttpStatus.BAD_GATEWAY, "BROKER_PROTOCOL_ERROR", e);
    }

    @ExceptionHandler(BrokerException.class)
    public ResponseEntity<ApiError> generic(BrokerException e) {
        return respond(HttpStatus.BAD_GATEWAY, "BROKER_ERROR", e);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String code, BrokerException e) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage(), e.operation()));
    }
}
