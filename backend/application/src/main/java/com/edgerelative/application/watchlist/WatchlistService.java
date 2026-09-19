package com.edgerelative.application.watchlist;

import com.edgerelative.application.watchlist.api.AddWatchlistItemRequest;
import com.edgerelative.application.watchlist.api.WatchlistEntry;
import com.edgerelative.application.watchlist.api.WatchlistResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String BROKER_CODE = "GROWW";
    private static final String BROKER_NAME = "Groww";
    private static final BigDecimal DEFAULT_TICK_SIZE = new BigDecimal("0.01");
    private static final long DEFAULT_LOT_SIZE = 1L;

    private static final Map<String, String> EXCHANGE_NAMES = Map.of(
            "NSE", "National Stock Exchange",
            "BSE", "BSE",
            "MCX", "Multi Commodity Exchange");

    private final DSLContext dsl;

    public WatchlistService(DSLContext dsl) {
        this.dsl = dsl;
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
        long exchangeId = ensureExchange(request.exchange());
        long instrumentId = ensureInstrument(exchangeId, request);
        if (isNotBlank(request.brokerSymbol())) {
            ensureBrokerMapping(instrumentId, request.brokerSymbol().trim());
        }
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
                    WatchlistException.DUPLICATE,
                    "%s is already on the active watchlist".formatted(request.symbol()));
        }
        int slot = nextSlot(watchlistId);
        dsl.execute(
                "INSERT INTO operational.watchlist_item (watchlist_id, instrument_id, slot) VALUES (?, ?, ?)",
                watchlistId,
                instrumentId,
                slot);
        return fetchEntry(watchlistId, instrumentId);
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

    // --- canonical reference data -------------------------------------------------

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

    private long ensureExchange(String exchangeCode) {
        String code = exchangeCode.trim().toUpperCase(Locale.ROOT);
        String name = EXCHANGE_NAMES.getOrDefault(code, code);
        Record record = dsl.fetchOne(
                "INSERT INTO reference.exchange (code, name, timezone, currency_code) VALUES (?, ?, 'Asia/Kolkata', 'INR') "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING exchange_id",
                code,
                name);
        return record.get("exchange_id", Long.class);
    }

    private long ensureInstrument(long exchangeId, AddWatchlistItemRequest request) {
        String exchange = request.exchange().trim().toUpperCase(Locale.ROOT);
        String segment = request.segment().trim().toUpperCase(Locale.ROOT);
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        String type = canonicalInstrumentType(request.instrumentType());
        UUID key = deterministicKey("instrument:%s:%s:%s:%s".formatted(exchange, segment, type, symbol));
        BigDecimal tickSize = request.tickSize() == null ? DEFAULT_TICK_SIZE : request.tickSize();
        long lotSize = request.lotSize() == null || request.lotSize() <= 0 ? DEFAULT_LOT_SIZE : request.lotSize();
        Record record = dsl.fetchOne(
                "INSERT INTO reference.instrument (instrument_key, exchange_id, instrument_type, segment, "
                        + "canonical_symbol, display_name, currency_code, tick_size, lot_size, trading_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'INR', ?, ?, 'ACTIVE') "
                        + "ON CONFLICT (instrument_key) DO UPDATE SET display_name = EXCLUDED.display_name, "
                        + "tick_size = EXCLUDED.tick_size, lot_size = EXCLUDED.lot_size RETURNING instrument_id",
                key,
                exchangeId,
                type,
                segment,
                symbol,
                request.name(),
                tickSize,
                lotSize);
        return record.get("instrument_id", Long.class);
    }

    private void ensureBrokerMapping(long instrumentId, String brokerSymbol) {
        Record broker = dsl.fetchOne(
                "INSERT INTO reference.broker (code, name) VALUES (?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING broker_id",
                BROKER_CODE,
                BROKER_NAME);
        long brokerId = broker.get("broker_id", Long.class);

        Record open = dsl.fetchOne(
                "SELECT broker_instrument_mapping_id, broker_token FROM reference.broker_instrument_mapping "
                        + "WHERE broker_id = ? AND instrument_id = ? AND valid_to IS NULL",
                brokerId,
                instrumentId);
        if (open != null) {
            if (brokerSymbol.equals(open.get("broker_token", String.class))) {
                return;
            }
            dsl.execute(
                    "UPDATE reference.broker_instrument_mapping SET valid_to = CURRENT_TIMESTAMP "
                            + "WHERE broker_instrument_mapping_id = ?",
                    open.get("broker_instrument_mapping_id", Long.class));
        }
        // Release the token if it currently maps to a different instrument.
        dsl.execute(
                "UPDATE reference.broker_instrument_mapping SET valid_to = CURRENT_TIMESTAMP "
                        + "WHERE broker_id = ? AND valid_to IS NULL AND broker_token = ? AND instrument_id <> ?",
                brokerId,
                brokerSymbol,
                instrumentId);
        dsl.execute(
                "INSERT INTO reference.broker_instrument_mapping "
                        + "(broker_id, instrument_id, broker_token, broker_symbol, valid_from) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                brokerId,
                instrumentId,
                brokerSymbol,
                brokerSymbol);
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
                        BROKER_CODE,
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
                        BROKER_CODE,
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
                || isBlank(request.symbol())
                || isBlank(request.instrumentType())) {
            throw new WatchlistException(
                    WatchlistException.INVALID, "exchange, instrumentType and symbol are required");
        }
    }

    private static String canonicalInstrumentType(String brokerType) {
        String value = brokerType.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "EQ", "EQUITY" -> "EQUITY";
            case "IDX", "INDEX" -> "INDEX";
            case "ETF" -> "ETF";
            case "FUT", "FUTURE" -> "FUTURE";
            case "CE", "PE", "OPTION" -> "OPTION";
            default -> "OTHER";
        };
    }

    private static UUID deterministicKey(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
