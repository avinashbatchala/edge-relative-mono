package com.edgerelative.application.feature.engine;

import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.reference.NseTradingCalendar;

import java.util.List;

/**
 * Everything the pure engine needs for one instrument/timeframe: the subject series and the two
 * explicit benchmark series, plus the resolved identities and versioned parameters. It contains only
 * canonical internal representations — no broker types.
 */
public record FeatureContext(
        long instrumentId,
        String timeframe,
        List<AggregatedCandle> subjectCandles,
        List<AggregatedCandle> marketCandles,
        List<AggregatedCandle> sectorCandles,
        BenchmarkIdentity benchmark,
        FeaturePolicy policy,
        FeatureVersions versions,
        NseTradingCalendar calendar,
        String marketCode,
        String sectorCode) {

    public FeatureContext {
        subjectCandles = List.copyOf(subjectCandles);
        marketCandles = marketCandles == null ? List.of() : List.copyOf(marketCandles);
        sectorCandles = sectorCandles == null ? List.of() : List.copyOf(sectorCandles);
    }
}
