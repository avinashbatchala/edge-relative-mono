package com.edgerelative.application.watchlist.api;

import java.util.List;

/**
 * Reorders the active watchlist; must contain exactly the currently watched instrument ids.
 */
public record ReorderWatchlistRequest(List<Long> instrumentIds) {
}
