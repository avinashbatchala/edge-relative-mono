package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representative workload: an M5 close update for the active ~20-symbol watchlist plus a short
 * historical replay window. Correctness precedes micro-optimisation; this only guards against
 * pathological regressions. Timings are logged, not asserted tightly (DD-04 performance discipline).
 */
class FeaturePerformanceTest {

    private static final Logger LOG = LoggerFactory.getLogger(FeaturePerformanceTest.class);
    private static final int SYMBOLS = 20;

    @Test
    void twentySymbolM5CloseUpdateIsBounded() {
        FeatureEngine engine = new FeatureEngine();
        List<AggregatedCandle> market = sessionPair(500, 520);
        List<FeatureContext> contexts = new ArrayList<>();
        for (int i = 0; i < SYMBOLS; i++) {
            contexts.add(FeatureTestSupport.context(
                    i + 1, sessionPair(100 + i, 120 + i), market, List.of(), FeatureTestSupport.policy(), "M5"));
        }

        long started = System.nanoTime();
        for (FeatureContext context : contexts) {
            engine.snapshot(context);
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        LOG.info("Feature snapshot for {} symbols took {} ms", SYMBOLS, elapsedMillis);
        assertThat(elapsedMillis).isLessThan(10_000);
    }

    private static List<AggregatedCandle> sessionPair(long firstVolume, long secondVolume) {
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(FeatureTestSupport.session(LocalDate.of(2026, 9, 1), firstVolume));
        bars.addAll(FeatureTestSupport.session(LocalDate.of(2026, 9, 2), secondVolume));
        return bars;
    }
}
