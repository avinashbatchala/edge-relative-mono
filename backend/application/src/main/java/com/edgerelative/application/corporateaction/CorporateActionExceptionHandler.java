package com.edgerelative.application.corporateaction;

import com.edgerelative.application.broker.api.ApiError;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps corporate-action adjustment failures to stable application errors.
 */
@RestControllerAdvice
public class CorporateActionExceptionHandler {

    @ExceptionHandler(CorporateActionException.class)
    public ResponseEntity<ApiError> handle(CorporateActionException exception) {
        HttpStatus status = CorporateActionException.UNSUPPORTED.equals(exception.code())
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(exception.code(), exception.getMessage(), null));
    }
}
