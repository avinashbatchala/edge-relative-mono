package com.edgerelative.application.feature;

import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.PriceChange;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.feature.volume.BaselineEstimatorType;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.reference.NseTradingCalendar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared builders for deterministic feature unit tests.
 */
public final class FeatureTestSupport {

    private FeatureTestSupport() {
    }

    public static final NseTradingCalendar CALENDAR = new NseTradingCalendar(Set.of());

    public static FeaturePolicy policy() {
        return policy(3, new FeaturePolicy.Rvol(BaselineEstimatorType.MEAN, 50, 50, 50, 2, 0.1, 20));
    }

    public static FeaturePolicy policy(int atrLength, FeaturePolicy.Rvol rvol) {
        return new FeaturePolicy(
                120,
                new FeaturePolicy.Benchmark("NIFTY50"),
                new FeaturePolicy.Atr(Map.of("M5", atrLength, "D1", atrLength), atrLength, AtrSmoothing.WILDER),
                new FeaturePolicy.Rrs(PriceChange.CLOSE_TO_CLOSE, 3, 8, 8, 3, 500),
                rvol,
                new FeaturePolicy.Rve(3, 8),
                new FeaturePolicy.Structure(3, 20),
                new FeaturePolicy.DirectionalVolume(20));
    }

    public static FeatureContext context(
            long instrumentId,
            List<AggregatedCandle> subject,
            List<AggregatedCandle> market,
            List<AggregatedCandle> sector,
            FeaturePolicy policy,
            String timeframe) {
        return new FeatureContext(
                instrumentId,
                timeframe,
                subject,
                market,
                sector,
                benchmark(market, sector),
                policy,
                new FeatureVersions(policy),
                CALENDAR,
                "NIFTY50",
                sector == null || sector.isEmpty() ? null : "NIFTY-ENERGY");
    }

    private static BenchmarkIdentity benchmark(List<AggregatedCandle> market, List<AggregatedCandle> sector) {
        boolean hasSector = sector != null && !sector.isEmpty();
        return new BenchmarkIdentity(
                market == null || market.isEmpty() ? null : 99L,
                "NIFTY50",
                hasSector ? 7L : null,
                hasSector ? "NIFTY-ENERGY" : null,
                hasSector ? "nse-sector-test-v1" : null,
                hasSector ? 98L : null,
                null);
    }

    public static AggregatedCandle bar(
            String openIso,
            String closeIso,
            double open,
            double high,
            double low,
            double close,
            long volume) {
        return bar(openIso, closeIso, open, high, low, close, volume, true, "GOOD");
    }

    public static AggregatedCandle bar(
            String openIso,
            String closeIso,
            double open,
            double high,
            double low,
            double close,
            long volume,
            boolean complete,
            String quality) {
        return new AggregatedCandle(
                Instant.parse(openIso),
                Instant.parse(closeIso),
                decimal(open),
                decimal(high),
                decimal(low),
                decimal(close),
                volume,
                null,
                null,
                null,
                false,
                complete,
                quality,
                "er-aggregate-v1");
    }

    /**
     * A single daily bar covering the whole NSE session.
     */
    public static AggregatedCandle dailyBar(java.time.LocalDate date, long volume, boolean complete) {
        String open = date.atTime(9, 15).atZone(NseTradingCalendar.EXCHANGE_ZONE).toInstant().toString();
        String close = date.atTime(15, 30).atZone(NseTradingCalendar.EXCHANGE_ZONE).toInstant().toString();
        return bar(open, close, 100, 101, 99, 100, volume, complete, complete ? "GOOD" : "INCOMPLETE");
    }

    /**
     * Full 75-slot M5 session with a constant volume in every slot.
     */
    public static List<AggregatedCandle> session(java.time.LocalDate date, long volumePerSlot) {
        long[] volumes = new long[75];
        java.util.Arrays.fill(volumes, volumePerSlot);
        return session(date, volumes);
    }

    /**
     * Full 75-slot M5 session (09:15-15:30 IST) with per-slot volumes.
     */
    public static List<AggregatedCandle> session(java.time.LocalDate date, long[] slotVolumes) {
        java.util.List<AggregatedCandle> bars = new java.util.ArrayList<>();
        java.time.Instant open = date.atTime(9, 15).atZone(NseTradingCalendar.EXCHANGE_ZONE).toInstant();
        for (int i = 0; i < slotVolumes.length; i++) {
            java.time.Instant barOpen = open.plusSeconds(300L * i);
            java.time.Instant barClose = barOpen.plusSeconds(300);
            double close = 100.0 + i * 0.01;
            bars.add(bar(
                    barOpen.toString(),
                    barClose.toString(),
                    close - 0.01,
                    close + 0.02,
                    close - 0.02,
                    close,
                    slotVolumes[i]));
        }
        return bars;
    }

    public static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
