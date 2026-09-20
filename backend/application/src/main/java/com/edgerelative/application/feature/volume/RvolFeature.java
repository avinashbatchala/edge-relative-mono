package com.edgerelative.application.feature.volume;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.engine.BarQuality;
import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.policy.FeaturePolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily, interval and cumulative time-of-day-normalised relative volume (DD-02 §44–§48, DD-05
 * §152–§158).
 *
 * <p>Every baseline uses only prior valid sessions. The opening/midday/closing volume curve is
 * respected by slot and cumulative-time alignment. The current session is never used as its own
 * baseline and future slots are never read.
 */
public final class RvolFeature {

    public record Result(
            Metric[] daily,
            Metric[] interval,
            Metric[] cumulative,
            Metric[] directionalLong,
            Metric[] directionalShort) {
    }

    public Result compute(
            BarSeries series,
            FeaturePolicy.Rvol parameters,
            int directionalVolumeWindow,
            SessionModel session,
            VolumeBaselineEstimator estimator) {
        int size = series.size();
        Metric[] daily = new Metric[size];
        Metric[] interval = new Metric[size];
        Metric[] cumulative = new Metric[size];
        Metric[] directionalLong = new Metric[size];
        Metric[] directionalShort = new Metric[size];

        LocalDate[] dates = new LocalDate[size];
        int[] sessionIndex = new int[size];
        int[] slot = new int[size];
        int[] tau = new int[size];
        long[] cumulativeInSession = new long[size];
        List<SessionAgg> sessions = new ArrayList<>();
        Map<LocalDate, Integer> sessionLookup = new HashMap<>();
        for (int i = 0; i < size; i++) {
            LocalDate date = session.sessionDate(series.openTime(i));
            Integer index = sessionLookup.get(date);
            if (index == null) {
                index = sessions.size();
                sessionLookup.put(date, index);
                sessions.add(new SessionAgg(date, session.expectedBars()));
            }
            dates[i] = date;
            sessionIndex[i] = index;
            slot[i] = session.slotIndex(series.openTime(i));
            tau[i] = session.minutesSinceOpen(series.closeTime(i));
            cumulativeInSession[i] = sessions.get(index).add(
                    series.volume(i), slot[i], tau[i], series.complete(i), BarQuality.quality(series, i));
        }

        long[] upVolume = new long[size + 1];
        long[] downVolume = new long[size + 1];
        for (int i = 0; i < size; i++) {
            long up = series.close(i) > series.open(i) ? series.volume(i) : 0L;
            long down = series.close(i) < series.open(i) ? series.volume(i) : 0L;
            upVolume[i + 1] = upVolume[i] + up;
            downVolume[i + 1] = downVolume[i] + down;
        }

        for (int i = 0; i < size; i++) {
            FeatureQuality barQuality = BarQuality.quality(series, i);
            boolean complete = series.complete(i);
            FeatureAvailability barAvailability =
                    complete ? FeatureAvailability.VALID : FeatureAvailability.INCOMPLETE;
            int currentSlot = slot[i];
            int currentTau = tau[i];

            daily[i] = relative(
                    complete,
                    barAvailability,
                    barQuality,
                    cumulativeInSession[i],
                    baseline(parameters.dailyLookback(), parameters.minSamples(), estimator, sessionIndex[i], sessions,
                            (sessionAgg, target) -> sessionAgg.totalVolumeIfComplete()));
            interval[i] = relative(
                    complete,
                    barAvailability,
                    barQuality,
                    series.volume(i),
                    baseline(parameters.intervalLookback(), parameters.minSamples(), estimator, sessionIndex[i], sessions,
                            (sessionAgg, target) -> sessionAgg.volumeAtSlot(currentSlot)));
            cumulative[i] = relative(
                    complete,
                    barAvailability,
                    barQuality,
                    cumulativeInSession[i],
                    baseline(parameters.cumulativeLookback(), parameters.minSamples(), estimator, sessionIndex[i], sessions,
                            (sessionAgg, target) -> sessionAgg.cumulativeUpTo(currentTau)));

            int start = Math.max(0, i - directionalVolumeWindow + 1);
            long up = upVolume[i + 1] - upVolume[start];
            long down = downVolume[i + 1] - downVolume[start];
            FeatureQuality windowQuality = FeatureQuality.GOOD;
            for (int j = start; j <= i; j++) {
                windowQuality = FeatureQuality.worst(windowQuality, BarQuality.quality(series, j));
            }
            directionalLong[i] = ratio(up, down, windowQuality);
            directionalShort[i] = ratio(down, up, windowQuality);
        }
        return new Result(daily, interval, cumulative, directionalLong, directionalShort);
    }

    private static Metric relative(
            boolean complete,
            FeatureAvailability barAvailability,
            FeatureQuality barQuality,
            double current,
            Baseline baseline) {
        if (!complete) {
            return Metric.unavailable(barAvailability, FeatureQuality.INCOMPLETE, "incomplete bar");
        }
        if (!baseline.sufficient()) {
            return Metric.unavailable(
                    FeatureAvailability.INSUFFICIENT_HISTORY, FeatureQuality.INCOMPLETE, "baseline samples=" + baseline.count);
        }
        if (!Double.isFinite(baseline.value) || baseline.value <= 0.0) {
            return Metric.unavailable(FeatureAvailability.INVALID, FeatureQuality.INCOMPLETE, "baseline<=0");
        }
        return Metric.numeric(current / baseline.value, barQuality);
    }

    private static Metric ratio(long numerator, long denominator, FeatureQuality quality) {
        if (denominator > 0) {
            return Metric.numeric((double) numerator / denominator, quality);
        }
        return Metric.unavailable(FeatureAvailability.INVALID, FeatureQuality.INCOMPLETE, "zero volume window");
    }

    private interface SampleSource {
        /**
         * @return sample for a prior session, or null if that session cannot contribute.
         */
        Double sample(SessionAgg session, int targetTau);
    }

    private static Baseline baseline(
            int lookback,
            int minSamples,
            VolumeBaselineEstimator estimator,
            int currentSessionIndex,
            List<SessionAgg> sessions,
            SampleSource source) {
        List<Double> samples = new ArrayList<>();
        for (int index = currentSessionIndex - 1; index >= 0 && samples.size() < lookback; index--) {
            Double sample = source.sample(sessions.get(index), 0);
            if (sample != null) {
                samples.add(sample);
            }
        }
        if (samples.size() < minSamples) {
            return new Baseline(Double.NaN, samples.size(), false);
        }
        double[] ordered = new double[samples.size()];
        for (int i = 0; i < ordered.length; i++) {
            ordered[ordered.length - 1 - i] = samples.get(i);
        }
        return new Baseline(estimator.estimate(ordered), ordered.length, true);
    }

    private record Baseline(double value, int count, boolean sufficient) {
    }

    /**
     * Per-session aggregation used by every baseline.
     */
    private static final class SessionAgg {
        private final int expectedBars;
        private final Map<Integer, Long> slotVolume = new HashMap<>();
        private final List<long[]> cumulativeByBar = new ArrayList<>();
        private long totalVolume;
        private int maxTau = -1;
        private boolean complete;

        SessionAgg(LocalDate date, int expectedBars) {
            this.expectedBars = expectedBars;
            this.complete = expectedBars > 0;
        }

        long add(long volume, int slot, int tau, boolean barComplete, FeatureQuality quality) {
            totalVolume += volume;
            slotVolume.merge(slot, volume, Long::sum);
            cumulativeByBar.add(new long[]{tau, totalVolume});
            maxTau = Math.max(maxTau, tau);
            if (cumulativeByBar.size() > expectedBars || !barComplete || !quality.trustworthy()) {
                complete = false;
            }
            return totalVolume;
        }

        Double totalVolumeIfComplete() {
            return complete ? (double) totalVolume : null;
        }

        Double volumeAtSlot(int slot) {
            if (!complete) {
                return null;
            }
            Long volume = slotVolume.get(slot);
            return volume == null ? null : volume.doubleValue();
        }

        Double cumulativeUpTo(int targetTau) {
            if (!complete || maxTau < targetTau) {
                return null;
            }
            long cumulative = 0L;
            for (long[] entry : cumulativeByBar) {
                if (entry[0] <= targetTau) {
                    cumulative = entry[1];
                } else {
                    break;
                }
            }
            return (double) cumulative;
        }
    }
}
