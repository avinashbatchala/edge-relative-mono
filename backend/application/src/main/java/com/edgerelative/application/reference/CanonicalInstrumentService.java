package com.edgerelative.application.reference;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

/**
 * Resolves broker-neutral identity to canonical reference rows.
 *
 * <p>Deterministic canonical keys mean repeated calls are idempotent and broker tokens never become
 * identity: they are stored only in {@code reference.broker_instrument_mapping}.
 */
@Service
public class CanonicalInstrumentService {

    public static final String BROKER_CODE = "GROWW";
    private static final String BROKER_NAME = "Groww";
    private static final BigDecimal DEFAULT_TICK_SIZE = new BigDecimal("0.01");
    private static final long DEFAULT_LOT_SIZE = 1L;

    private static final Map<String, String> EXCHANGE_NAMES = Map.of(
            "NSE", "National Stock Exchange",
            "BSE", "BSE",
            "MCX", "Multi Commodity Exchange");

    private final DSLContext dsl;

    public CanonicalInstrumentService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long ensureExchange(String exchangeCode) {
        String code = exchangeCode.trim().toUpperCase(Locale.ROOT);
        String name = EXCHANGE_NAMES.getOrDefault(code, code);
        Record record = dsl.fetchOne(
                "INSERT INTO reference.exchange (code, name, timezone, currency_code) VALUES (?, ?, 'Asia/Kolkata', 'INR') "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING exchange_id",
                code,
                name);
        return record.get("exchange_id", Long.class);
    }

    public long ensureInstrument(
            String exchange,
            String segment,
            String instrumentType,
            String symbol,
            String name,
            BigDecimal tickSize,
            Long lotSize) {
        String exchangeCode = exchange.trim().toUpperCase(Locale.ROOT);
        String segmentCode = segment.trim().toUpperCase(Locale.ROOT);
        String symbolCode = symbol.trim().toUpperCase(Locale.ROOT);
        String type = canonicalInstrumentType(instrumentType);
        long exchangeId = ensureExchange(exchangeCode);
        UUID key = deterministicKey("instrument:%s:%s:%s:%s".formatted(exchangeCode, segmentCode, type, symbolCode));
        BigDecimal effectiveTick = tickSize == null ? DEFAULT_TICK_SIZE : tickSize;
        long effectiveLot = lotSize == null || lotSize <= 0 ? DEFAULT_LOT_SIZE : lotSize;
        Record record = dsl.fetchOne(
                "INSERT INTO reference.instrument (instrument_key, exchange_id, instrument_type, segment, "
                        + "canonical_symbol, display_name, currency_code, tick_size, lot_size, trading_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'INR', ?, ?, 'ACTIVE') "
                        + "ON CONFLICT (instrument_key) DO UPDATE SET display_name = EXCLUDED.display_name, "
                        + "tick_size = EXCLUDED.tick_size, lot_size = EXCLUDED.lot_size RETURNING instrument_id",
                key,
                exchangeId,
                type,
                segmentCode,
                symbolCode,
                name,
                effectiveTick,
                effectiveLot);
        return record.get("instrument_id", Long.class);
    }

    public void ensureBrokerMapping(long instrumentId, String brokerSymbol) {
        if (brokerSymbol == null || brokerSymbol.isBlank()) {
            return;
        }
        String token = brokerSymbol.trim();
        long brokerId = ensureBroker();
        Record open = dsl.fetchOne(
                "SELECT broker_instrument_mapping_id, broker_token FROM reference.broker_instrument_mapping "
                        + "WHERE broker_id = ? AND instrument_id = ? AND valid_to IS NULL",
                brokerId,
                instrumentId);
        if (open != null) {
            if (token.equals(open.get("broker_token", String.class))) {
                return;
            }
            dsl.execute(
                    "UPDATE reference.broker_instrument_mapping SET valid_to = CURRENT_TIMESTAMP "
                            + "WHERE broker_instrument_mapping_id = ?",
                    open.get("broker_instrument_mapping_id", Long.class));
        }
        dsl.execute(
                "UPDATE reference.broker_instrument_mapping SET valid_to = CURRENT_TIMESTAMP "
                        + "WHERE broker_id = ? AND valid_to IS NULL AND broker_token = ? AND instrument_id <> ?",
                brokerId,
                token,
                instrumentId);
        dsl.execute(
                "INSERT INTO reference.broker_instrument_mapping "
                        + "(broker_id, instrument_id, broker_token, broker_symbol, valid_from) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                brokerId,
                instrumentId,
                token,
                token);
    }

    /**
     * Read-only timeframe lookup for the canonical read path. Registration is a write owned by
     * ingestion/reference startup, so readers must not mutate reference data.
     */
    public java.util.OptionalLong findTimeframeId(String code) {
        TimeframeCatalog.Spec spec = TimeframeCatalog.require(code);
        Record record = dsl.fetchOne("SELECT timeframe_id FROM reference.timeframe WHERE code = ?", spec.code());
        if (record == null) {
            return java.util.OptionalLong.empty();
        }
        return java.util.OptionalLong.of(record.get("timeframe_id", Long.class));
    }

    /**
     * Ensures the registry row exists and returns its id; rejects unregistered codes.
     */
    public long ensureTimeframe(String code) {
        TimeframeCatalog.Spec spec = TimeframeCatalog.require(code);
        Record record = dsl.fetchOne(
                "INSERT INTO reference.timeframe (code, duration_seconds, calendar_based) VALUES (?, ?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET duration_seconds = EXCLUDED.duration_seconds, "
                        + "calendar_based = EXCLUDED.calendar_based, active = TRUE RETURNING timeframe_id",
                spec.code(),
                spec.durationSeconds(),
                spec.calendarBased());
        return record.get("timeframe_id", Long.class);
    }

    private long ensureBroker() {
        Record record = dsl.fetchOne(
                "INSERT INTO reference.broker (code, name) VALUES (?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING broker_id",
                BROKER_CODE,
                BROKER_NAME);
        return record.get("broker_id", Long.class);
    }

    private static String canonicalInstrumentType(String brokerType) {
        String value = brokerType == null ? "" : brokerType.trim().toUpperCase(Locale.ROOT);
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
}
