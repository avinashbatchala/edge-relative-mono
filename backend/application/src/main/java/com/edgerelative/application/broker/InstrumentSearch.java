package com.edgerelative.application.broker;

import com.edgerelative.broker.api.model.BrokerInstrument;
import java.util.List;
import java.util.Locale;

/**
 * Case-insensitive instrument lookup over a broker instrument master.
 *
 * <p>The master is large (~140k rows); returning it whole to a browser is expensive, so the API
 * supports a bounded search. Unbounded full-master reads remain available for tooling.
 */
public final class InstrumentSearch {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private InstrumentSearch() {
    }

    public static List<BrokerInstrument> filter(List<BrokerInstrument> instruments, String query, Integer limit) {
        int bounded = bound(limit);
        if (query == null || query.isBlank()) {
            return instruments.stream().limit(bounded).toList();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return instruments.stream().filter(instrument -> matches(instrument, needle)).limit(bounded).toList();
    }

    private static boolean matches(BrokerInstrument instrument, String needle) {
        return contains(instrument.tradingSymbol(), needle)
                || contains(instrument.brokerSymbol(), needle)
                || contains(instrument.name(), needle)
                || contains(instrument.isin(), needle)
                || contains(instrument.underlyingSymbol(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static int bound(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
