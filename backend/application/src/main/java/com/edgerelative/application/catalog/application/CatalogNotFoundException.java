package com.edgerelative.application.catalog.application;

/** Requested catalog entity does not exist; surfaced to the API as 404. */
public class CatalogNotFoundException extends RuntimeException {

    public CatalogNotFoundException(String message) {
        super(message);
    }
}
