package com.edgerelative.application.watchlist;

import com.edgerelative.application.broker.api.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps watchlist constraint failures to stable application errors. */
@RestControllerAdvice
public class WatchlistExceptionHandler {

    @ExceptionHandler(WatchlistException.class)
    public ResponseEntity<ApiError> handle(WatchlistException exception) {
        HttpStatus status = switch (exception.code()) {
            case WatchlistException.FULL, WatchlistException.DUPLICATE -> HttpStatus.CONFLICT;
            case WatchlistException.NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ApiError(exception.code(), exception.getMessage(), null));
    }
}
