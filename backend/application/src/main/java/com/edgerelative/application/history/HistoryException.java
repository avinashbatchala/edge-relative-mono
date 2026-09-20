package com.edgerelative.application.history;

/**
 * Typed historical-data failure mapped to a stable application error code.
 */
public class HistoryException extends RuntimeException {

    public static final String INVALID = "HISTORY_INVALID";
    public static final String NOT_FOUND = "HISTORY_NOT_FOUND";
    public static final String NOT_WATCHED = "HISTORY_INSTRUMENT_NOT_WATCHED";

    private final String code;

    public HistoryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
