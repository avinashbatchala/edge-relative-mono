package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerInstrument;
import com.edgerelative.broker.api.model.BrokerInstrumentType;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Case-insensitive, relevance-ordered instrument lookup over a broker instrument master.
 *
 * <p>The master is large (~140k rows) and lists derivatives before cash equities. A raw substring
 * filter therefore buries the underlying an operator is looking for under dozens of futures/options
 * contracts. Results are ranked so an exact ticker or company-name match on the cash equity comes
 * first, then the index, then derivatives (which remain available but secondary).
 */
public final class InstrumentSearch {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private static final long EXACT_SYMBOL = 1_000_000;
    private static final long SYMBOL_PREFIX = 600_000;
    private static final long SYMBOL_CONTAINS = 300_000;
    private static final long BROKER_SYMBOL_MATCH = 100_000;
    private static final long NAME_MATCH = 80_000;
    private static final long ISIN_MATCH = 60_000;
    private static final long UNDERLYING_MATCH = 20_000;

    private static final long TYPE_EQUITY = 5_000;
    private static final long TYPE_OTHER = 2_000;
    private static final long TYPE_INDEX = 4_000;
    private static final long TYPE_DERIVATIVE = 1_000;

    private InstrumentSearch() {
    }

    public static List<BrokerInstrument> filter(List<BrokerInstrument> instruments, String query, Integer limit) {
        return filter(instruments, query, limit, null, true);
    }

    /**
     * Relevance-ordered lookup with an optional exchange restriction and an option to exclude
     * futures/options. Filtering happens before ranking and the limit, so a bounded slice is never
     * crowded out by off-exchange or derivative rows.
     */
    public static List<BrokerInstrument> filter(
            List<BrokerInstrument> instruments,
            String query,
            Integer limit,
            BrokerExchange exchange,
            boolean includeDerivatives) {
        int bounded = bound(limit);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return instruments.stream()
                .filter(instrument -> exchange == null || instrument.exchange() == exchange)
                .filter(instrument -> includeDerivatives || !isDerivative(instrument))
                .map(instrument -> score(instrument, needle))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(Scored::score)
                        .reversed()
                        .thenComparing(scored -> nullToEmpty(scored.instrument().tradingSymbol())))
                .limit(bounded)
                .map(Scored::instrument)
                .toList();
    }

    private static boolean isDerivative(BrokerInstrument instrument) {
        BrokerInstrumentType type = instrument.instrumentType();
        return type == BrokerInstrumentType.FUT
                || type == BrokerInstrumentType.CE
                || type == BrokerInstrumentType.PE;
    }

    private static Scored score(BrokerInstrument instrument, String needle) {
        long score = typeScore(instrument);
        if (needle.isEmpty()) {
            return new Scored(instrument, score);
        }

        boolean matched = false;
        String symbol = lower(instrument.tradingSymbol());
        if (symbol != null) {
            if (symbol.equals(needle)) {
                score += EXACT_SYMBOL;
                matched = true;
            } else if (symbol.startsWith(needle)) {
                score += SYMBOL_PREFIX;
                matched = true;
            } else if (symbol.contains(needle)) {
                score += SYMBOL_CONTAINS;
                matched = true;
            }
        }
        if (contains(instrument.brokerSymbol(), needle)) {
            score += BROKER_SYMBOL_MATCH;
            matched = true;
        }
        if (contains(instrument.name(), needle)) {
            score += NAME_MATCH;
            matched = true;
        }
        if (contains(instrument.isin(), needle)) {
            score += ISIN_MATCH;
            matched = true;
        }
        if (contains(instrument.underlyingSymbol(), needle)) {
            score += UNDERLYING_MATCH;
            matched = true;
        }
        return matched ? new Scored(instrument, score) : null;
    }

    private static long typeScore(BrokerInstrument instrument) {
        BrokerInstrumentType type = instrument.instrumentType();
        if (type == BrokerInstrumentType.EQ) {
            return TYPE_EQUITY;
        }
        if (type == BrokerInstrumentType.IDX) {
            return TYPE_INDEX;
        }
        if (type == BrokerInstrumentType.FUT || type == BrokerInstrumentType.CE || type == BrokerInstrumentType.PE) {
            return TYPE_DERIVATIVE;
        }
        return TYPE_OTHER;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int bound(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private record Scored(BrokerInstrument instrument, long score) {
    }
}
