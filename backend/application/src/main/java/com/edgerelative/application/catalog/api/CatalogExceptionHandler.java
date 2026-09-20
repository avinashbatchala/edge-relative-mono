package com.edgerelative.application.catalog.api;

import com.edgerelative.application.catalog.application.CatalogNotFoundException;
import com.edgerelative.application.catalog.application.CatalogValidationException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps catalog failures to explicit HTTP statuses with structured messages. */
@RestControllerAdvice(basePackages = "com.edgerelative.application.catalog.api")
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> validation(CatalogValidationException exception) {
        return Map.of("errors", List.of(exception.getMessage()));
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(CatalogNotFoundException exception) {
        return Map.of("errors", List.of(exception.getMessage()));
    }
}
