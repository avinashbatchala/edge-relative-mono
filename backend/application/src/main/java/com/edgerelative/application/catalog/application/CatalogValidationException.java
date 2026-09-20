package com.edgerelative.application.catalog.application;

/** Invalid or conflicting catalog input; surfaced to the API as 422. */
public class CatalogValidationException extends RuntimeException {

    public CatalogValidationException(String message) {
        super(message);
    }
}
