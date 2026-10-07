package com.edgerelative.application.system.api;

import java.time.Instant;

/**
 * Authoritative current trading mode and safety controls. The declared mode comes from
 * {@code trading.mode}; the switches come from the persisted trading control state. When no control
 * row exists, {@code false} values are reported with {@code source=default} — they are the schema
 * defaults, not an assertion that controls are engaged.
 */
public record SystemModeResponse(
        String configuredMode,
        boolean realCapital,
        boolean executionEnabled,
        Control control,
        String modeSource,
        Instant asOf) {

    public record Control(
            boolean stopNewTrades,
            boolean cancelPendingEntries,
            boolean flattenOnly,
            boolean automationEnabled,
            boolean executionEnabled,
            String reason,
            String updatedBy,
            Instant updatedAt,
            boolean present) {
    }
}
