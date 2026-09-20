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

    @Test
    void insufficientSessionsLeaveRvolUnavailableRatherThanOne() {
        RvolFeature.Result result = computeDaily(List.of(FeatureTestSupport.dailyBar(D1, 100, true)), 2);
        assertThat(result.daily()[0].availability()).isEqualTo(FeatureAvailability.INSUFFICIENT_HISTORY);
        assertThat(result.daily()[0].value()).isNull();
    }
}
