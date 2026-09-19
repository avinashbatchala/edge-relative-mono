package com.edgerelative.broker.groww.client;

import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.model.BrokerInstrumentType;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.groww.mapper.GrowwMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the Groww instrument master CSV.
 *
 * <p>Required fields are validated and malformed rows fail with a row number, rather than producing a
 * silently incomplete master. "NaN" is treated as absent, never as a value. Broker identifiers are
 * preserved for temporal mapping into Edge Relative reference data; they are not identity.
 */
public class GrowwInstrumentCsvParser {

    private final GrowwMapper mapper;

    public GrowwInstrumentCsvParser(GrowwMapper mapper) {
        this.mapper = mapper;
    }

    public List<BrokerInstrument> parse(String csv) {
        List<List<String>> rows = readCsv(csv);
        if (rows.isEmpty()) {
            throw new BrokerProtocolException("Instrument master was empty", "groww", "INSTRUMENT_MASTER", null, null);
        }
        Map<String, Integer> header = new HashMap<>();
        List<String> headerRow = rows.get(0);
        for (int i = 0; i < headerRow.size(); i++) {
            header.put(headerRow.get(i).trim().toLowerCase(), i);
        }
        requireColumn(header, "exchange");
        requireColumn(header, "trading_symbol");

        List<BrokerInstrument> instruments = new ArrayList<>(rows.size() - 1);
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.isEmpty() || row.stream().allMatch(String::isBlank)) {
                continue;
            }
            instruments.add(toInstrument(header, row, r + 1));
        }
        return instruments;
    }

    private BrokerInstrument toInstrument(Map<String, Integer> header, List<String> row, int lineNumber) {
        String exchangeRaw = value(header, row, "exchange");
        String tradingSymbol = value(header, row, "trading_symbol");
        if (exchangeRaw == null || exchangeRaw.isBlank()) {
            throw malformed(lineNumber, "exchange is required");
        }
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            throw malformed(lineNumber, "trading_symbol is required");
        }
        try {
            BrokerExchange exchange = mapper.exchange(exchangeRaw);
            BrokerSegment segment = mapper.segmentOrNull(value(header, row, "segment"));
            return new BrokerInstrument(
                    exchange,
                    value(header, row, "exchange_token"),
                    tradingSymbol,
                    value(header, row, "groww_symbol"),
                    value(header, row, "name"),
                    mapper.instrumentType(value(header, row, "instrument_type")),
                    segment,
                    value(header, row, "series"),
                    value(header, row, "isin"),
                    value(header, row, "underlying_symbol"),
                    value(header, row, "underlying_exchange_token"),
                    parseLong(value(header, row, "lot_size"), 1L, lineNumber, "lot_size"),
                    parseDate(value(header, row, "expiry_date"), lineNumber),
                    parseDecimal(value(header, row, "strike_price"), lineNumber, "strike_price"),
                    parseDecimal(value(header, row, "tick_size"), lineNumber, "tick_size"),
                    parseNullableLong(value(header, row, "freeze_quantity")),
                    parseBoolean(value(header, row, "is_reserved")),
                    parseBoolean(value(header, row, "buy_allowed")),
                    parseBoolean(value(header, row, "sell_allowed")));
        } catch (RuntimeException e) {
            if (e instanceof BrokerProtocolException protocol && e.getMessage() != null
                    && e.getMessage().contains("row " + lineNumber)) {
                throw protocol;
            }
            throw malformed(lineNumber, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private BrokerProtocolException malformed(int line, String detail) {
        return new BrokerProtocolException(
                "Malformed instrument master row " + line + ": " + detail, "groww", "INSTRUMENT_MASTER", null, null);
    }

    private static void requireColumn(Map<String, Integer> header, String name) {
        if (!header.containsKey(name)) {
            throw new BrokerProtocolException(
                    "Instrument master missing required column '" + name + "'", "groww", "INSTRUMENT_MASTER", null, null);
        }
    }

    private static String value(Map<String, Integer> header, List<String> row, String name) {
        Integer index = header.get(name);
        if (index == null || index >= row.size()) {
            return null;
        }
        String raw = row.get(index);
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() || "NaN".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private static long parseLong(String raw, long defaultValue, int line, String field) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BrokerProtocolException(
                    "Instrument master row " + line + " has invalid " + field + ": " + raw,
                    "groww",
                    "INSTRUMENT_MASTER",
                    null,
                    e);
        }
    }

    private static Long parseNullableLong(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String raw, int line, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new BrokerProtocolException(
                    "Instrument master row " + line + " has invalid " + field + ": " + raw,
                    "groww",
                    "INSTRUMENT_MASTER",
                    null,
                    e);
        }
    }

    private static LocalDate parseDate(String raw, int line) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new BrokerProtocolException(
                    "Instrument master row " + line + " has invalid expiry_date: " + raw,
                    "groww",
                    "INSTRUMENT_MASTER",
                    null,
                    e);
        }
    }

    private static boolean parseBoolean(String raw) {
        return raw != null && ("true".equalsIgnoreCase(raw) || "1".equals(raw));
    }

    /** Minimal RFC-4180-ish reader: supports quoted fields and commas within quotes. */
    static List<List<String>> readCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
