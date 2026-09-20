package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.math.PriceChange;
import com.edgerelative.application.feature.relative.RrsFeature;
import com.edgerelative.application.feature.relative.RrsParameters;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RrsFeatureTest {

    private static final Instant BASE = Instant.parse("2026-09-01T03:45:00Z");

    private static RrsParameters parameters() {
        return new RrsParameters(1, AtrSmoothing.SIMPLE, PriceChange.CLOSE_TO_CLOSE, 1, 1, 1, 1, 5);
    }

    /**
     * Bars with open = previous close and no wicks beyond the move, so TR = |change| and ATR = 1.
     */
    private static BarSeries trending(double... closes) {
        List<AggregatedCandle> bars = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            double open = i == 0 ? closes[0] - 1 : closes[i - 1];
            double high = Math.max(open, closes[i]);
            double low = Math.min(open, closes[i]);
            bars.add(bar(i, open, high, low, closes[i], 100, true, "GOOD"));
        }
        return BarSeries.of(bars);
    }

    private static BarSeries flat(double level, int count) {
        List<AggregatedCandle> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bars.add(bar(i, level, level + 0.5, level - 0.5, level, 100, true, "GOOD"));
        }
        return BarSeries.of(bars);
    }

    private static AggregatedCandle bar(
            int index, double open, double high, double low, double close, long volume, boolean complete, String quality) {
        Instant barOpen = BASE.plusSeconds(300L * index);
        return FeatureTestSupport.bar(
                barOpen.toString(), barOpen.plusSeconds(300).toString(), open, high, low, close, volume, complete, quality);
    }

    @Test
    void identicalNormalisedMovementYieldsZero() {
        RrsFeature.Result result = new RrsFeature().compute(trending(100, 101, 102), trending(200, 201, 202), parameters());
        assertThat(result.rawAvailability()[2]).isEqualTo(FeatureAvailability.VALID);
        assertThat(result.raw()[2]).isEqualTo(0.0);
    }

    @Test
    void strongerStockYieldsPositiveAndWeakerYieldsNegative() {
        RrsFeature feature = new RrsFeature();
        assertThat(feature.compute(trending(100, 101, 102), flat(200, 3), parameters()).raw()[2]).isEqualTo(1.0);
        assertThat(feature.compute(flat(100, 3), trending(200, 201, 202), parameters()).raw()[2]).isEqualTo(-1.0);
    }

    @Test
    void benchmarkTimestampMismatchIsAStaleQualityCondition() {
        BarSeries misaligned = BarSeries.of(List.of(
                bar(0, 200, 200.5, 199.5, 200, 100, true, "GOOD"),
                FeatureTestSupport.bar(
                        BASE.plusSeconds(360).toString(),
                        BASE.plusSeconds(660).toString(),
                        200, 200.5, 199.5, 200, 100)));
        RrsFeature.Result result = new RrsFeature().compute(trending(100, 101, 102), misaligned, parameters());
        assertThat(result.rawAvailability()[2]).isEqualTo(FeatureAvailability.STALE);
        assertThat(result.rawQuality()[2]).isEqualTo(FeatureQuality.STALE);
        assertThat(result.raw()[2]).isNaN();
    }

    @Test
    void staleBenchmarkBarStillComputesButPropagatesQuality() {
        RrsFeature.Result result = new RrsFeature().compute(
                trending(100, 101, 102), flatWithQuality(200, 3, "STALE"), parameters());
        assertThat(result.rawAvailability()[2]).isEqualTo(FeatureAvailability.VALID);
        assertThat(result.rawQuality()[2]).isEqualTo(FeatureQuality.STALE);
        assertThat(result.raw()[2]).isEqualTo(1.0);
    }

    @Test
    void incompleteSubjectBarHasNoFinalizedRrs() {
        BarSeries incomplete = BarSeries.of(List.of(
                bar(0, 99, 100, 99, 100, 100, true, "GOOD"),
                bar(1, 100, 101, 100, 101, 100, false, "INCOMPLETE"),
                bar(2, 101, 102, 101, 102, 100, false, "INCOMPLETE")));
        RrsFeature.Result result = new RrsFeature().compute(incomplete, flat(200, 3), parameters());
        assertThat(result.rawAvailability()[2]).isEqualTo(FeatureAvailability.INCOMPLETE);
        assertThat(result.raw()[2]).isNaN();
    }

    @Test
    void changingAtrLengthChangesTheResultUnderTheSameSemanticVersion() {
        List<AggregatedCandle> varying = List.of(
                bar(0, 99, 100.5, 99.5, 100, 100, true, "GOOD"),
                bar(1, 109, 112, 108, 110, 100, true, "GOOD"),
                bar(2, 119, 120.5, 118, 120, 100, true, "GOOD"));
        BarSeries benchmark = flat(200, 3);
        RrsParameters shortAtr = new RrsParameters(1, AtrSmoothing.SIMPLE, PriceChange.CLOSE_TO_CLOSE, 1, 1, 1, 1, 5);
        RrsParameters longAtr = new RrsParameters(2, AtrSmoothing.SIMPLE, PriceChange.CLOSE_TO_CLOSE, 1, 1, 1, 1, 5);
        RrsFeature feature = new RrsFeature();
        double shortRaw = feature.compute(BarSeries.of(varying), benchmark, shortAtr).raw()[2];
        double longRaw = feature.compute(BarSeries.of(varying), benchmark, longAtr).raw()[2];
        assertThat(shortRaw).isGreaterThan(longRaw);
    }

    private static BarSeries flatWithQuality(double level, int count, String quality) {
        List<AggregatedCandle> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bars.add(bar(i, level, level + 0.5, level - 0.5, level, 100, true, quality));
        }
        return BarSeries.of(bars);
    }
}
