package com.edgerelative.application.history;

import com.edgerelative.application.broker.api.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps historical-data failures to stable application errors.
 */
@RestControllerAdvice
public class HistoryExceptionHandler {

    @ExceptionHandler(HistoryException.class)
    public ResponseEntity<ApiError> handle(HistoryException exception) {
        HttpStatus status = switch (exception.code()) {
            case HistoryException.NOT_FOUND -> HttpStatus.NOT_FOUND;
            case HistoryException.NOT_WATCHED -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.code(), exception.getMessage(), null));
    }
}
