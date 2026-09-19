package com.edgerelative.application.watchlist;

/** Typed watchlist constraint failure mapped to a stable application error code. */
public class WatchlistException extends RuntimeException {

    public static final String FULL = "WATCHLIST_FULL";
    public static final String DUPLICATE = "WATCHLIST_DUPLICATE";
    public static final String INVALID = "WATCHLIST_INVALID";
    public static final String NOT_FOUND = "WATCHLIST_ITEM_NOT_FOUND";

    private final String code;

    public WatchlistException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
