package com.edgerelative.application.history.api;

import java.util.List;

/**
 * One page of backfill runs for an instrument, with the total count for pagination.
 */
public record BackfillRunsPage(List<BackfillRunResponse> items, long total) {

    public BackfillRunsPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
