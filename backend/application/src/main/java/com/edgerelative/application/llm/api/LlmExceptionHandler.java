package com.edgerelative.application.llm.api;

import com.edgerelative.application.broker.api.ApiError;
import com.edgerelative.llm.api.error.LlmAuthenticationException;
import com.edgerelative.llm.api.error.LlmException;
import com.edgerelative.llm.api.error.LlmRateLimitException;
import com.edgerelative.llm.api.error.LlmTimeoutException;
import com.edgerelative.llm.api.error.LlmUnavailableException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps advisory LLM failures to the shared {@link ApiError} envelope. Provider error codes are
 * never exposed; only a stable classification.
 */
@RestControllerAdvice
public class LlmExceptionHandler {

    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(LlmUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("LLM_UNAVAILABLE", exception.getMessage(), null));
    }

    @ExceptionHandler(LlmAuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(LlmAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("LLM_NOT_CONFIGURED", exception.getMessage(), null));
    }

    @ExceptionHandler(LlmRateLimitException.class)
    public ResponseEntity<ApiError> handleRateLimit(LlmRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("LLM_RATE_LIMITED", exception.getMessage(), null));
    }

    @ExceptionHandler(LlmTimeoutException.class)
    public ResponseEntity<ApiError> handleTimeout(LlmTimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiError("LLM_TIMEOUT", exception.getMessage(), null));
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiError> handleGeneric(LlmException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("LLM_PROVIDER_ERROR", exception.getMessage(), null));
    }
}
