package com.edgerelative.application.watchlist.api;

import java.util.List;

/**
 * Active watchlist with its capacity so the UI can show remaining slots.
 */
public record WatchlistResponse(
        String name, int capacity, int count, List<WatchlistEntry> entries) {

    public WatchlistResponse {
        entries = List.copyOf(entries);
    }
}
