package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.volume.BaselineEstimatorType;
import com.edgerelative.application.feature.volume.RvolFeature;
import com.edgerelative.application.feature.volume.SessionModel;
import com.edgerelative.application.feature.volume.VolumeBaselineEstimators;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RvolFeatureTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);
    private static final LocalDate D4 = LocalDate.of(2026, 9, 4);

    private static RvolFeature.Result computeDaily(List<AggregatedCandle> bars, int minSamples) {
        FeaturePolicy policy = FeatureTestSupport.policy(
                3, new FeaturePolicy.Rvol(BaselineEstimatorType.MEAN, 50, 50, 50, minSamples, 0.1, 20));
        return compute(bars, policy, 375);
    }

    private static RvolFeature.Result computeIntraday(List<AggregatedCandle> bars, int minSamples) {
        FeaturePolicy policy = FeatureTestSupport.policy(
                3, new FeaturePolicy.Rvol(BaselineEstimatorType.MEAN, 50, 50, 50, minSamples, 0.1, 20));
        return compute(bars, policy, 5);
    }

    private static RvolFeature.Result compute(List<AggregatedCandle> bars, FeaturePolicy policy, int timeframeMinutes) {
        SessionModel model = new SessionModel(FeatureTestSupport.CALENDAR, timeframeMinutes);
        return new RvolFeature().compute(
                BarSeries.of(bars),
                policy.rvol(),
                policy.directionalVolume().window(),
                model,
                VolumeBaselineEstimators.of(BaselineEstimatorType.MEAN, 0.1, 20));
    }

    @Test
    void dailyRvolUsesOnlyPriorValidSessions() {
        RvolFeature.Result result = computeDaily(
                List.of(
                        FeatureTestSupport.dailyBar(D1, 100, true),
                        FeatureTestSupport.dailyBar(D2, 100, true),
                        FeatureTestSupport.dailyBar(D3, 200, true)),
                2);
        assertThat(result.daily()[0].availability()).isEqualTo(FeatureAvailability.INSUFFICIENT_HISTORY);
        assertThat(result.daily()[1].availability()).isEqualTo(FeatureAvailability.INSUFFICIENT_HISTORY);
        assertThat(result.daily()[2].value()).isEqualTo(2.0);
        assertThat(result.daily()[2].quality().name()).isEqualTo("GOOD");
    }

    @Test
    void invalidPriorSessionIsExcludedFromTheBaseline() {
        RvolFeature.Result result = computeDaily(
                List.of(
                        FeatureTestSupport.dailyBar(D1, 100, true),
                        FeatureTestSupport.dailyBar(D2, 1000, false),
                        FeatureTestSupport.dailyBar(D3, 100, true),
                        FeatureTestSupport.dailyBar(D4, 200, true)),
                2);
        // Only D1 and D3 are valid prior sessions -> baseline 100, not (100+1000+100)/3.
        assertThat(result.daily()[3].value()).isEqualTo(2.0);
    }

    @Test
    void intervalRvolComparesTheSameSessionSlot() {
        long[] base = new long[75];
        java.util.Arrays.fill(base, 10);
        long[] opening = base.clone();
        opening[0] = 100;
        long[] anomaly = base.clone();
        anomaly[0] = 20;
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(FeatureTestSupport.session(D1, opening));
        bars.addAll(FeatureTestSupport.session(D2, opening));
        bars.addAll(FeatureTestSupport.session(D3, anomaly));

        RvolFeature.Result result = computeIntraday(bars, 2);
        // D3 slot 0: 20 / mean(100,100) = 0.2
        assertThat(result.interval()[150].value()).isEqualTo(0.2);
        // D3 slot 5: 10 / mean(10,10) = 1.0 (opening volume must not contaminate midday)
        assertThat(result.interval()[155].value()).isEqualTo(1.0);
    }

    @Test
    void openingVolumeDoesNotContaminateMiddayBaseline() {
        long[] curve = new long[75];
        java.util.Arrays.fill(curve, 10);
        curve[0] = 100;
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(FeatureTestSupport.session(D1, curve));
        bars.addAll(FeatureTestSupport.session(D2, curve));
        bars.addAll(FeatureTestSupport.session(D3, curve));

        RvolFeature.Result result = computeIntraday(bars, 2);
        // Midday slot 37 baseline is 10, not the day average.
        assertThat(result.interval()[150 + 37].value()).isEqualTo(1.0);
    }

    @Test
    void cumulativeRvolComparesEquivalentSessionRelativeTime() {
        long[] base = new long[75];
        java.util.Arrays.fill(base, 10);
        long[] doubled = new long[75];
        java.util.Arrays.fill(doubled, 20);
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(FeatureTestSupport.session(D1, base));
        bars.addAll(FeatureTestSupport.session(D2, base));
        bars.addAll(FeatureTestSupport.session(D3, doubled));

        RvolFeature.Result result = computeIntraday(bars, 2);
        int anchor = 150 + 10;
        // 20*11 / (10*11) = 2.0
        assertThat(result.cumulative()[anchor].value()).isEqualTo(2.0);
    }

    @Test
    void noFutureSlotLeaksIntoAnEarlierAnchor() {
        long[] base = new long[75];
        java.util.Arrays.fill(base, 10);
        long[] future = base.clone();
        future[74] = 100000;
        List<AggregatedCandle> shortHistory = new ArrayList<>();
        shortHistory.addAll(FeatureTestSupport.session(D1, base));
        shortHistory.addAll(FeatureTestSupport.session(D2, base));
        shortHistory.addAll(FeatureTestSupport.session(D3, base).subList(0, 11));
        List<AggregatedCandle> longHistory = new ArrayList<>(shortHistory);
        longHistory.addAll(FeatureTestSupport.session(D3, future).subList(11, 75));

        RvolFeature.Result shortResult = computeIntraday(shortHistory, 2);
        RvolFeature.Result longResult = computeIntraday(longHistory, 2);
        int anchor = 150 + 10;
        assertThat(shortResult.cumulative()[anchor].value()).isEqualTo(longResult.cumulative()[anchor].value());
        assertThat(shortResult.interval()[anchor].value()).isEqualTo(longResult.interval()[anchor].value());
    }

    /**
     * Six full H1 bars plus the truncated 15-minute final bar, all with the same volume.
     */
    private static List<AggregatedCandle> h1Session(LocalDate date, long volumePerBar) {
        Instant open = FeatureTestSupport.CALENDAR.sessionOpen(date);
        List<AggregatedCandle> bars = new ArrayList<>();
        for (int hour = 0; hour < 6; hour++) {
            Instant start = open.plusSeconds(3600L * hour);
            bars.add(FeatureTestSupport.bar(
                    start.toString(), start.plusSeconds(3600).toString(), 100, 100.5, 99.5, 100, volumePerBar));
        }
        Instant partial = open.plusSeconds(3600L * 6);
        bars.add(FeatureTestSupport.bar(
                partial.toString(),
                FeatureTestSupport.CALENDAR.sessionClose(date).toString(),
                100, 100.5, 99.5, 100, volumePerBar));
        return bars;
    }

    @Test
    void partialFinalBucketDoesNotInvalidateASessionBaseline() {
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(h1Session(D1, 10));
        bars.addAll(h1Session(D2, 10));
        bars.addAll(h1Session(D3, 10));
        FeaturePolicy policy = FeatureTestSupport.policy(
                3, new FeaturePolicy.Rvol(BaselineEstimatorType.MEAN, 50, 50, 50, 2, 0.1, 20));

        RvolFeature.Result result = compute(bars, policy, 60);

        // Each prior H1 session total is 7 x 10 = 70; D3's first bar is 10 -> 10/70.
        int firstBarOfD3 = 14;
        assertThat(result.daily()[firstBarOfD3].availability()).isEqualTo(FeatureAvailability.VALID);
        assertThat(result.daily()[firstBarOfD3].value()).isCloseTo(10.0 / 70.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void truncatedPriorSessionIsExcludedFromTheDailyBaseline() {
        long[] full = new long[75];
        java.util.Arrays.fill(full, 10);
        long[] padded = new long[75];
        java.util.Arrays.fill(padded, 1000);
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(FeatureTestSupport.session(D1, full));
        bars.addAll(FeatureTestSupport.session(D2, full));
        // D3 is truncated to 74 of 75 bars but otherwise well-formed: it must not enter the baseline.
        bars.addAll(FeatureTestSupport.session(D3, padded).subList(0, 74));
        bars.addAll(FeatureTestSupport.session(D4, full));

        RvolFeature.Result result = computeIntraday(bars, 2);

        int lastBar = bars.size() - 1;
        // D4 cumulative 750 / mean(D1 750, D2 750); the truncated D3 is excluded, so the ratio is 1.
        assertThat(result.daily()[lastBar].value()).isEqualTo(1.0);
    }

    @Test
    void insufficientSessionsLeaveRvolUnavailableRatherThanOne() {
        RvolFeature.Result result = computeDaily(List.of(FeatureTestSupport.dailyBar(D1, 100, true)), 2);
        assertThat(result.daily()[0].availability()).isEqualTo(FeatureAvailability.INSUFFICIENT_HISTORY);
        assertThat(result.daily()[0].value()).isNull();
    }
}
