package com.edgerelative.application.corporateaction;

/**
 * Typed corporate-action adjustment failure mapped to a stable application error code.
 */
public class CorporateActionException extends RuntimeException {

    public static final String UNSUPPORTED = "CORPORATE_ACTION_ADJUSTMENT_UNSUPPORTED";
    public static final String INVALID = "CORPORATE_ACTION_ADJUSTMENT_INVALID";

    private final String code;

    public CorporateActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
