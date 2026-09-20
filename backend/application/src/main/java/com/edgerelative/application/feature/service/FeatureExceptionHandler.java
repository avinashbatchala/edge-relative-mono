package com.edgerelative.application.feature.service;

import com.edgerelative.application.broker.api.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps feature-query failures to stable application errors.
 */
@RestControllerAdvice
public class FeatureExceptionHandler {

    @ExceptionHandler(FeatureException.class)
    public ResponseEntity<ApiError> handle(FeatureException exception) {
        HttpStatus status = switch (exception.code()) {
            case FeatureException.NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.code(), exception.getMessage(), null));
    }
}
