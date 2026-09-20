package com.edgerelative.application.feature.service;

/**
 * Typed feature-query failure mapped to a stable application error code.
 */
public class FeatureException extends RuntimeException {

    public static final String INVALID = "FEATURE_INVALID";
    public static final String NOT_FOUND = "FEATURE_NOT_FOUND";

    private final String code;

    public FeatureException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
