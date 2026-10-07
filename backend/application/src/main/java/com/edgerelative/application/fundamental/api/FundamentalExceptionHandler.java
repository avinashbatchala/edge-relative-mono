package com.edgerelative.application.fundamental.api;

import com.edgerelative.application.broker.api.ApiError;
import com.edgerelative.fundamentals.api.error.FundamentalAuthenticationException;
import com.edgerelative.fundamentals.api.error.FundamentalException;
import com.edgerelative.fundamentals.api.error.FundamentalNotFoundException;
import com.edgerelative.fundamentals.api.error.FundamentalUnavailableException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps fundamental-data failures to the shared {@link ApiError} envelope. The provider error code is
 * never exposed; only a stable classification.
 */
@RestControllerAdvice
public class FundamentalExceptionHandler {

    @ExceptionHandler(FundamentalNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(FundamentalNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("FUNDAMENTAL_NOT_FOUND", exception.getMessage(), null));
    }

    @ExceptionHandler(FundamentalUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(FundamentalUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("FUNDAMENTAL_UNAVAILABLE", exception.getMessage(), null));
    }

    @ExceptionHandler(FundamentalAuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(FundamentalAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("FUNDAMENTAL_PROVIDER_ERROR", exception.getMessage(), null));
    }

    @ExceptionHandler(FundamentalException.class)
    public ResponseEntity<ApiError> handleGeneric(FundamentalException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("FUNDAMENTAL_PROVIDER_ERROR", exception.getMessage(), null));
    }
}
