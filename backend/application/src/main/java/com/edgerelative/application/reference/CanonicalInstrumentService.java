package com.edgerelative.application.reference;

import com.edgerelative.broker.api.model.BrokerCandleInterval;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private static final Map<BrokerCandleInterval, TimeframeDefinition> TIMEFRAMES = Map.ofEntries(
            Map.entry(BrokerCandleInterval.ONE_MINUTE, new TimeframeDefinition("M1", 60, false)),
            Map.entry(BrokerCandleInterval.TWO_MINUTE, new TimeframeDefinition("M2", 120, false)),
            Map.entry(BrokerCandleInterval.THREE_MINUTE, new TimeframeDefinition("M3", 180, false)),
            Map.entry(BrokerCandleInterval.FIVE_MINUTE, new TimeframeDefinition("M5", 300, false)),
            Map.entry(BrokerCandleInterval.TEN_MINUTE, new TimeframeDefinition("M10", 600, false)),
            Map.entry(BrokerCandleInterval.FIFTEEN_MINUTE, new TimeframeDefinition("M15", 900, false)),
            Map.entry(BrokerCandleInterval.THIRTY_MINUTE, new TimeframeDefinition("M30", 1800, false)),
            Map.entry(BrokerCandleInterval.ONE_HOUR, new TimeframeDefinition("H1", 3600, false)),
            Map.entry(BrokerCandleInterval.FOUR_HOUR, new TimeframeDefinition("H4", 14400, false)),
            Map.entry(BrokerCandleInterval.ONE_DAY, new TimeframeDefinition("D1", null, true)),
            Map.entry(BrokerCandleInterval.ONE_WEEK, new TimeframeDefinition("W1", null, true)),
            Map.entry(BrokerCandleInterval.ONE_MONTH, new TimeframeDefinition("MN1", null, true)));

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

    public long ensureTimeframe(BrokerCandleInterval interval) {
        TimeframeDefinition definition = TIMEFRAMES.get(interval);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported timeframe: " + interval);
        }
        Record record = dsl.fetchOne(
                "INSERT INTO reference.timeframe (code, duration_seconds, calendar_based) VALUES (?, ?, ?) "
                        + "ON CONFLICT (code) DO UPDATE SET duration_seconds = EXCLUDED.duration_seconds, "
                        + "calendar_based = EXCLUDED.calendar_based RETURNING timeframe_id",
                definition.code(),
                definition.durationSeconds(),
                definition.calendarBased());
        return record.get("timeframe_id", Long.class);
    }

    public static String timeframeCode(BrokerCandleInterval interval) {
        TimeframeDefinition definition = TIMEFRAMES.get(interval);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported timeframe: " + interval);
        }
        return definition.code();
    }

    /** Fixed bar length for intraday timeframes; {@code null} for calendar-based (D1/W1/MN1). */
    public static Duration barDuration(BrokerCandleInterval interval) {
        TimeframeDefinition definition = TIMEFRAMES.get(interval);
        if (definition == null || definition.durationSeconds() == null) {
            return null;
        }
        return Duration.ofSeconds(definition.durationSeconds());
    }

    public static boolean isCalendarBased(BrokerCandleInterval interval) {
        TimeframeDefinition definition = TIMEFRAMES.get(interval);
        return definition != null && definition.calendarBased();
    }

    public static BrokerCandleInterval intervalForTimeframeCode(String code) {
        return TIMEFRAMES.entrySet().stream()
                .filter(entry -> entry.getValue().code().equals(code))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown timeframe code: " + code));
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

    private record TimeframeDefinition(String code, Integer durationSeconds, boolean calendarBased) {
    }
}
