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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the Groww instrument master CSV.
 *
 * <p>The exchange master is large (tens of thousands of rows) and occasionally contains a row with a
 * missing identity or an unparseable value. A single bad row must not fail the entire download, so
 * such rows are skipped and counted/logged, while structural problems (empty file, missing required
 * column) still fail. "NaN" is treated as absent, never as a value. Broker identifiers are preserved
 * for temporal mapping into Edge Relative reference data; they are not identity.
 */
public class GrowwInstrumentCsvParser {

    private static final Logger LOG = LoggerFactory.getLogger(GrowwInstrumentCsvParser.class);
    private static final int MAX_SAMPLE_LINE_NUMBERS = 5;

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
        List<Integer> skippedSampleLines = new ArrayList<>();
        int skipped = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.isEmpty() || row.stream().allMatch(String::isBlank)) {
                continue;
            }
            try {
                instruments.add(toInstrument(header, row));
            } catch (RuntimeException e) {
                // Any row-level defect (missing identity, unknown enum, bad number) skips only that row.
                skipped++;
                if (skippedSampleLines.size() < MAX_SAMPLE_LINE_NUMBERS) {
                    skippedSampleLines.add(r + 1);
                }
            }
        }
        if (skipped > 0 && LOG.isWarnEnabled()) {
            LOG.warn(
                    "Skipped {} malformed row(s) in the Groww instrument master (sample line numbers {}); parsed {} instruments",
                    skipped,
                    skippedSampleLines,
                    instruments.size());
        }
        return instruments;
    }

    private BrokerInstrument toInstrument(Map<String, Integer> header, List<String> row) {
        String exchangeRaw = value(header, row, "exchange");
        String tradingSymbol = value(header, row, "trading_symbol");
        if (exchangeRaw == null || exchangeRaw.isBlank()) {
            throw new RowProblem("exchange is required");
        }
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            throw new RowProblem("trading_symbol is required");
        }
        return new BrokerInstrument(
                mapper.exchange(exchangeRaw),
                value(header, row, "exchange_token"),
                tradingSymbol,
                value(header, row, "groww_symbol"),
                value(header, row, "name"),
                mapper.instrumentType(value(header, row, "instrument_type")),
                mapper.segmentOrNull(value(header, row, "segment")),
                value(header, row, "series"),
                value(header, row, "isin"),
                value(header, row, "underlying_symbol"),
                value(header, row, "underlying_exchange_token"),
                parseLong(value(header, row, "lot_size"), 1L, "lot_size"),
                parseDate(value(header, row, "expiry_date")),
                parseDecimal(value(header, row, "strike_price"), "strike_price"),
                parseDecimal(value(header, row, "tick_size"), "tick_size"),
                parseNullableLong(value(header, row, "freeze_quantity")),
                parseBoolean(value(header, row, "is_reserved")),
                parseBoolean(value(header, row, "buy_allowed")),
                parseBoolean(value(header, row, "sell_allowed")));
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

    private static long parseLong(String raw, long defaultValue, String field) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new RowProblem("invalid " + field + ": " + raw);
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

    private static BigDecimal parseDecimal(String raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new RowProblem("invalid " + field + ": " + raw);
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new RowProblem("invalid expiry_date: " + raw);
        }
    }

    private static boolean parseBoolean(String raw) {
        return raw != null && ("true".equalsIgnoreCase(raw) || "1".equals(raw));
    }

    /** Row-level defect: skip the row, keep the rest of the master. */
    private static final class RowProblem extends RuntimeException {
        RowProblem(String message) {
            super(message);
        }
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
