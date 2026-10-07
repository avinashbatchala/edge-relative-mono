package com.edgerelative.application.system;

import java.time.Instant;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Read-only access to the persisted trading control switches (DD-03 safety controls). There is no
 * runtime producer that writes these rows yet; when absent the caller must report defaults rather
 * than assume controls are engaged.
 */
@Repository
public class TradingControlStateRepository {

    private final DSLContext dsl;

    public TradingControlStateRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * The most recently updated control row across all scopes. A single-operator deployment keeps one
     * row; multi-account scopes are not yet surfaced by the UI.
     */
    public Optional<TradingControlState> latest() {
        Record record = dsl.fetchOne(
                "SELECT stop_new_trades, cancel_pending_entries, flatten_only, automation_enabled, "
                        + "execution_enabled, control_reason, updated_by, updated_at "
                        + "FROM operational.trading_control_state "
                        + "ORDER BY updated_at DESC, state_version DESC LIMIT 1");
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(new TradingControlState(
                Boolean.TRUE.equals(record.get("stop_new_trades", Boolean.class)),
                Boolean.TRUE.equals(record.get("cancel_pending_entries", Boolean.class)),
                Boolean.TRUE.equals(record.get("flatten_only", Boolean.class)),
                Boolean.TRUE.equals(record.get("automation_enabled", Boolean.class)),
                Boolean.TRUE.equals(record.get("execution_enabled", Boolean.class)),
                record.get("control_reason", String.class),
                record.get("updated_by", String.class),
                record.get("updated_at", Instant.class)));
    }

    /** Immutable snapshot of the control switches. */
    public record TradingControlState(
            boolean stopNewTrades,
            boolean cancelPendingEntries,
            boolean flattenOnly,
            boolean automationEnabled,
            boolean executionEnabled,
            String reason,
            String updatedBy,
            Instant updatedAt) {
    }
}
