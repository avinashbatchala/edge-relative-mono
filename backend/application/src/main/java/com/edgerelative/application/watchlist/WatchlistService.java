package com.edgerelative.application.watchlist;

import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.watchlist.api.AddWatchlistItemRequest;
import com.edgerelative.application.watchlist.api.WatchlistEntry;
import com.edgerelative.application.watchlist.api.WatchlistResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Watchlist application service.
 *
 * <p>Persists canonical instrument identity in the existing {@code operational.watchlist} /
 * {@code reference.instrument} tables. Broker tokens live only in
 * {@code reference.broker_instrument_mapping} and are surfaced as a mapping detail for market-data
 * calls; they are never identity.
 */
@Service
public class WatchlistService {

    public static final int CAPACITY = 20;

    private static final String DEFAULT_TENANT_NAME = "Default Operator";
    private static final UUID DEFAULT_TENANT_KEY = deterministicKey("edge-relative:default-tenant");
    private static final String ACTIVE_WATCHLIST_NAME = "Active";

    private final DSLContext dsl;
    private final CanonicalInstrumentService canonical;

    public WatchlistService(DSLContext dsl, CanonicalInstrumentService canonical) {
        this.dsl = dsl;
        this.canonical = canonical;
    }

    @Transactional
    public WatchlistResponse list() {
        long tenantId = ensureTenant();
        long watchlistId = ensureWatchlist(tenantId);
        List<WatchlistEntry> entries = fetchEntries(watchlistId);
        return new WatchlistResponse(ACTIVE_WATCHLIST_NAME, CAPACITY, entries.size(), entries);
    }

    @Transactional
    public WatchlistEntry add(AddWatchlistItemRequest request) {
        validate(request);
        long tenantId = ensureTenant();
        long instrumentId = canonical.ensureInstrument(
                request.exchange(),
                request.segment(),
                request.instrumentType(),
                request.symbol(),
                request.name(),
                request.tickSize(),
                request.lotSize());
        long watchlistId = ensureWatchlist(tenantId);
        int count = countItems(watchlistId);
        if (count >= CAPACITY) {
            throw new WatchlistException(
                    WatchlistException.FULL,
                    "Active watchlist is full (%d/%d). Remove an instrument first."
                            .formatted(CAPACITY, CAPACITY));
        }
        if (containsInstrument(watchlistId, instrumentId)) {
            throw new WatchlistException(
                    WatchlistException.DUPLICATE, "%s is already on the active watchlist".formatted(request.symbol()));
        }
        // Reference mutation happens only after the watchlist guards pass: a duplicate/full rejection
        // must not roll back (or pretend to apply) a broker-token change.
        canonical.ensureBrokerMapping(instrumentId, request.brokerSymbol());
        int slot = nextSlot(watchlistId);
        dsl.execute(
                "INSERT INTO operational.watchlist_item (watchlist_id, instrument_id, slot) VALUES (?, ?, ?)",
                watchlistId,
                instrumentId,
                slot);
        return fetchEntry(watchlistId, instrumentId);
    }

    /**
     * Whether the instrument is on the active watchlist. Read-only: does not create a tenant or
     * watchlist, so it is safe to call as a guard from other application services.
     */
    public boolean isWatched(long instrumentId) {
        return dsl.fetchOne(
                "SELECT 1 AS present FROM operational.watchlist_item wi "
                        + "JOIN operational.watchlist w ON w.watchlist_id = wi.watchlist_id "
                        + "WHERE w.active = TRUE AND wi.instrument_id = ?",
                instrumentId)
                != null;
    }

    @Transactional
    public void remove(long instrumentId) {
        long tenantId = ensureTenant();
        long watchlistId = ensureWatchlist(tenantId);
        int deleted = dsl.execute(
                "DELETE FROM operational.watchlist_item WHERE watchlist_id = ? AND instrument_id = ?",
                watchlistId,
                instrumentId);
        if (deleted == 0) {
            throw new WatchlistException(
                    WatchlistException.NOT_FOUND, "Instrument %d is not on the active watchlist".formatted(instrumentId));
        }
    }

    @Transactional
    public WatchlistResponse reorder(List<Long> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            throw new WatchlistException(WatchlistException.INVALID, "instrumentIds must not be empty");
        }
        if (instrumentIds.size() > CAPACITY) {
            throw new WatchlistException(WatchlistException.INVALID, "Watchlist cannot exceed %d items".formatted(CAPACITY));
        }
        long tenantId = ensureTenant();
        long watchlistId = ensureWatchlist(tenantId);
        List<Long> current = dsl.fetch(
                        "SELECT instrument_id FROM operational.watchlist_item WHERE watchlist_id = ? ORDER BY slot",
                        watchlistId)
                .getValues("instrument_id", Long.class);
        Set<Long> currentSet = new HashSet<>(current);
        Set<Long> requested = new HashSet<>(instrumentIds);
        if (currentSet.size() != requested.size() || !currentSet.equals(requested)) {
            throw new WatchlistException(
                    WatchlistException.INVALID, "instrumentIds must match the current watchlist exactly");
        }
        // Rebuild slots deterministically; avoids unique-slot collisions while permuting.
        dsl.execute("DELETE FROM operational.watchlist_item WHERE watchlist_id = ?", watchlistId);
        int slot = 1;
        for (Long instrumentId : instrumentIds) {
            dsl.execute(
                    "INSERT INTO operational.watchlist_item (watchlist_id, instrument_id, slot) VALUES (?, ?, ?)",
                    watchlistId,
                    instrumentId,
                    slot++);
        }
        return list();
    }

    // --- tenant / watchlist -------------------------------------------------------

    private long ensureTenant() {
        Record record = dsl.fetchOne(
                "INSERT INTO operational.tenant (tenant_key, name) VALUES (?, ?) "
                        + "ON CONFLICT (tenant_key) DO UPDATE SET name = EXCLUDED.name RETURNING tenant_id",
                DEFAULT_TENANT_KEY,
                DEFAULT_TENANT_NAME);
        return record.get("tenant_id", Long.class);
    }

    private long ensureWatchlist(long tenantId) {
        Record record = dsl.fetchOne(
                "INSERT INTO operational.watchlist (watchlist_key, tenant_id, name, active) VALUES (?, ?, ?, TRUE) "
                        + "ON CONFLICT (tenant_id, name) DO UPDATE SET active = TRUE RETURNING watchlist_id",
                deterministicKey("edge-relative:default-watchlist"),
                tenantId,
                ACTIVE_WATCHLIST_NAME);
        return record.get("watchlist_id", Long.class);
    }

    // --- queries ------------------------------------------------------------------

    private List<WatchlistEntry> fetchEntries(long watchlistId) {
        return dsl.fetch(
                        "SELECT wi.slot, i.instrument_id, i.instrument_key, e.code AS exchange, i.segment, "
                                + "i.instrument_type, i.canonical_symbol, i.display_name, i.tick_size, i.lot_size, "
                                + "bm.broker_token AS broker_symbol "
                                + "FROM operational.watchlist_item wi "
                                + "JOIN reference.instrument i ON i.instrument_id = wi.instrument_id "
                                + "JOIN reference.exchange e ON e.exchange_id = i.exchange_id "
                                + "LEFT JOIN reference.broker b ON b.code = ? "
                                + "LEFT JOIN reference.broker_instrument_mapping bm ON bm.instrument_id = i.instrument_id "
                                + "AND bm.broker_id = b.broker_id AND bm.valid_to IS NULL "
                                + "WHERE wi.watchlist_id = ? ORDER BY wi.slot",
                        CanonicalInstrumentService.BROKER_CODE,
                        watchlistId)
                .map(WatchlistService::toEntry);
    }

    private WatchlistEntry fetchEntry(long watchlistId, long instrumentId) {
        return dsl.fetchOne(
                        "SELECT wi.slot, i.instrument_id, i.instrument_key, e.code AS exchange, i.segment, "
                                + "i.instrument_type, i.canonical_symbol, i.display_name, i.tick_size, i.lot_size, "
                                + "bm.broker_token AS broker_symbol "
                                + "FROM operational.watchlist_item wi "
                                + "JOIN reference.instrument i ON i.instrument_id = wi.instrument_id "
                                + "JOIN reference.exchange e ON e.exchange_id = i.exchange_id "
                                + "LEFT JOIN reference.broker b ON b.code = ? "
                                + "LEFT JOIN reference.broker_instrument_mapping bm ON bm.instrument_id = i.instrument_id "
                                + "AND bm.broker_id = b.broker_id AND bm.valid_to IS NULL "
                                + "WHERE wi.watchlist_id = ? AND wi.instrument_id = ?",
                        CanonicalInstrumentService.BROKER_CODE,
                        watchlistId,
                        instrumentId)
                .map(WatchlistService::toEntry);
    }

    private static WatchlistEntry toEntry(Record record) {
        return new WatchlistEntry(
                record.get("instrument_id", Long.class),
                record.get("instrument_key", UUID.class),
                record.get("exchange", String.class),
                record.get("segment", String.class),
                record.get("instrument_type", String.class),
                record.get("canonical_symbol", String.class),
                record.get("display_name", String.class),
                record.get("broker_symbol", String.class),
                record.get("tick_size", BigDecimal.class),
                record.get("lot_size", Long.class),
                record.get("slot", Integer.class));
    }

    private int countItems(long watchlistId) {
        Long count = dsl.fetchOne(
                        "SELECT count(*) AS c FROM operational.watchlist_item WHERE watchlist_id = ?", watchlistId)
                .get("c", Long.class);
        return count == null ? 0 : count.intValue();
    }

    private boolean containsInstrument(long watchlistId, long instrumentId) {
        return dsl.fetchOne(
                "SELECT 1 AS present FROM operational.watchlist_item WHERE watchlist_id = ? AND instrument_id = ?",
                watchlistId,
                instrumentId)
                != null;
    }

    private int nextSlot(long watchlistId) {
        return dsl.fetchOne(
                        "SELECT min(s) AS slot FROM generate_series(1, ?) s "
                                + "WHERE s NOT IN (SELECT slot FROM operational.watchlist_item WHERE watchlist_id = ?)",
                        CAPACITY,
                        watchlistId)
                .get("slot", Integer.class);
    }

    // --- helpers ------------------------------------------------------------------

    private static void validate(AddWatchlistItemRequest request) {
        if (request == null
                || isBlank(request.exchange())
                || isBlank(request.segment())
                || isBlank(request.symbol())
                || isBlank(request.instrumentType())) {
            throw new WatchlistException(
                    WatchlistException.INVALID, "exchange, segment, instrumentType and symbol are required");
        }
        // Reference constraints require tick_size > 0 and lot_size > 0; reject at the boundary rather
        // than coercing a caller mistake into a different instrument definition.
        if (request.tickSize() != null && request.tickSize().signum() <= 0) {
            throw new WatchlistException(WatchlistException.INVALID, "tickSize must be > 0");
        }
        if (request.lotSize() != null && request.lotSize() <= 0) {
            throw new WatchlistException(WatchlistException.INVALID, "lotSize must be > 0");
        }
    }

    private static UUID deterministicKey(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
