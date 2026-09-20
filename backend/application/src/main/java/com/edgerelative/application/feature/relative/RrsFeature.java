package com.edgerelative.application.feature.relative;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.engine.BarQuality;
import com.edgerelative.application.feature.math.Atr;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.math.Ema;
import com.edgerelative.application.feature.math.RollingStatistics;

import java.util.Arrays;

/**
 * The single canonical Real Relative Strength implementation (DD-02 §33, DD-05 §143–§150).
 *
 * <p>{@code RRS_raw = (dP_stock / ATR_stock) - (dP_benchmark / ATR_benchmark)}. Subject and benchmark
 * bars must share the exact canonical interval close timestamp; a mismatch is a quality condition and
 * yields no value, never a nearest/latest substitute. The derived fast/slow/persistence/slope/
 * acceleration/percentile/trend arrays are all causal.
 */
public final class RrsFeature {

    public static final String POSITIVE_RISING = "POSITIVE_RISING";
    public static final String POSITIVE_FALLING = "POSITIVE_FALLING";
    public static final String NEGATIVE_FALLING = "NEGATIVE_FALLING";
    public static final String NEGATIVE_RISING = "NEGATIVE_RISING";
    public static final String NEUTRAL = "NEUTRAL";

    public record Result(
            double[] raw,
            double[] atrSubject,
            double[] atrBenchmark,
            FeatureAvailability[] rawAvailability,
            FeatureQuality[] rawQuality,
            double[] fast,
            double[] slow,
            double[] persistence,
            double[] slope,
            double[] acceleration,
            double[] percentile,
            String[] trendState) {
    }

    public Result compute(BarSeries subject, BarSeries benchmark, RrsParameters parameters) {
        int size = subject.size();
        double[] atrSubject = Atr.series(
                high(subject), low(subject), close(subject), parameters.atrLength(), parameters.atrSmoothing());
        double[] atrBenchmark = benchmark == null
                ? new double[0]
                : Atr.series(
                high(benchmark),
                low(benchmark),
                close(benchmark),
                parameters.atrLength(),
                parameters.atrSmoothing());
        double[] raw = new double[size];
        FeatureAvailability[] availability = new FeatureAvailability[size];
        FeatureQuality[] quality = new FeatureQuality[size];
        Arrays.fill(raw, Double.NaN);
        for (int i = 0; i < size; i++) {
            availability[i] = FeatureAvailability.MISSING_INPUT;
            quality[i] = FeatureQuality.UNAVAILABLE;
            if (!subject.complete(i)) {
                availability[i] = FeatureAvailability.INCOMPLETE;
                quality[i] = FeatureQuality.INCOMPLETE;
                continue;
            }
            if (benchmark == null) {
                continue;
            }
            int benchmarkIndex = benchmark.indexOfCloseTime(subject.closeTime(i));
            if (benchmarkIndex < 0) {
                // Timestamp misalignment is a data-quality condition, not an approximation (DD-05 §137).
                availability[i] = FeatureAvailability.STALE;
                quality[i] = FeatureQuality.STALE;
                continue;
            }
            FeatureQuality subjectQuality = BarQuality.quality(subject, i);
            FeatureQuality benchmarkQuality = BarQuality.quality(benchmark, benchmarkIndex);
            if (!Double.isFinite(atrSubject[i]) || !Double.isFinite(atrBenchmark[benchmarkIndex])) {
                availability[i] = FeatureAvailability.WARMING_UP;
                quality[i] = FeatureQuality.INCOMPLETE;
                continue;
            }
            double subjectChange = parameters.priceChange().of(subject, i);
            double benchmarkChange = parameters.priceChange().of(benchmark, benchmarkIndex);
            if (!Double.isFinite(subjectChange) || !Double.isFinite(benchmarkChange)) {
                availability[i] = FeatureAvailability.WARMING_UP;
                quality[i] = FeatureQuality.INCOMPLETE;
                continue;
            }
            double value = subjectChange / atrSubject[i] - benchmarkChange / atrBenchmark[benchmarkIndex];
            if (!Double.isFinite(value)) {
                // A finite-but-zero ATR makes the normalized move undefined (0/0 or x/0). A
                // degenerate volatility baseline is not a measurement: never emit it as VALID.
                availability[i] = FeatureAvailability.INVALID;
                quality[i] = FeatureQuality.UNAVAILABLE;
                continue;
            }
            raw[i] = value;
            availability[i] = FeatureAvailability.VALID;
            quality[i] = FeatureQuality.worst(subjectQuality, benchmarkQuality);
        }
        double[] fast = Ema.seriesSparse(raw, parameters.fastLength());
        double[] slow = Ema.seriesSparse(raw, parameters.slowLength());
        double[] acceleration = difference(fast);
        double[] slope = slope(fast, parameters.slopeLookback());
        double[] persistence = persistence(raw, fast, parameters.persistenceWindow());
        double[] percentile = percentile(raw, parameters.percentileWindow(), parameters.percentileMinSamples());
        String[] trend = trend(fast, acceleration);
        return new Result(
                raw,
                atrSubject,
                atrBenchmark,
                availability,
                quality,
                fast,
                slow,
                persistence,
                slope,
                acceleration,
                percentile,
                trend);
    }

    private static double[] difference(double[] values) {
        double[] result = new double[values.length];
        Arrays.fill(result, Double.NaN);
        for (int i = 1; i < values.length; i++) {
            if (Double.isFinite(values[i]) && Double.isFinite(values[i - 1])) {
                result[i] = values[i] - values[i - 1];
            }
        }
        return result;
    }

    private static double[] slope(double[] values, int lookback) {
        double[] result = new double[values.length];
        Arrays.fill(result, Double.NaN);
        for (int i = lookback; i < values.length; i++) {
            if (Double.isFinite(values[i]) && Double.isFinite(values[i - lookback])) {
                result[i] = (values[i] - values[i - lookback]) / lookback;
            }
        }
        return result;
    }

    /**
     * Share of the last {@code window} contiguous bars whose RRS raw sign agrees with RRS fast
     * (DD-02 §35, "percent of recent bars"). The window is strictly the previous {@code window} bars
     * including the current one: a gap (non-finite raw) makes the score unavailable rather than
     * reaching back past the gap, which would silently mix non-recent regime observations.
     */
    private static double[] persistence(double[] raw, double[] fast, int window) {
        double[] result = new double[window > 0 ? raw.length : 0];
        Arrays.fill(result, Double.NaN);
        for (int i = 0; i < raw.length; i++) {
            if (i + 1 < window || !Double.isFinite(fast[i]) || fast[i] == 0.0) {
                continue;
            }
            double referenceSign = Math.signum(fast[i]);
            boolean eligible = true;
            int agreeing = 0;
            for (int j = i - window + 1; j <= i; j++) {
                if (!Double.isFinite(raw[j])) {
                    eligible = false;
                    break;
                }
                if (Math.signum(raw[j]) == referenceSign) {
                    agreeing++;
                }
            }
            if (eligible) {
                result[i] = (double) agreeing / window;
            }
        }
        return result;
    }

    /**
     * Trailing point-in-time percentile of RRS raw (DD-05 §151). The window is the last {@code
     * window} observations available before or at {@code i} (inclusive of the current bar). A
     * versioned {@code minSamples} guard prevents a degenerate distribution: with fewer than {@code
     * minSamples} finite observations the percentile is unavailable rather than a trivial 1.0 from a
     * one-point sample. DD-02 §42 phrased this as "prior observations"; the inclusive reading of the
     * more specific DD-05 §151 is used, and the guard is the operator-controlled strictness.
     */
    private static double[] percentile(double[] raw, int window, int minSamples) {
        double[] result = new double[raw.length];
        Arrays.fill(result, Double.NaN);
        int required = Math.max(1, minSamples);
        for (int i = 0; i < raw.length; i++) {
            if (!Double.isFinite(raw[i])) {
                continue;
            }
            int start = Math.max(0, i - window + 1);
            double[] values = new double[i - start + 1];
            int count = 0;
            for (int j = start; j <= i; j++) {
                if (Double.isFinite(raw[j])) {
                    values[count++] = raw[j];
                }
            }
            if (count < required) {
                continue;
            }
            result[i] = RollingStatistics.percentileRank(Arrays.copyOf(values, count), raw[i]);
        }
        return result;
    }

    private static String[] trend(double[] fast, double[] acceleration) {
        String[] result = new String[fast.length];
        for (int i = 0; i < fast.length; i++) {
            if (!Double.isFinite(fast[i]) || !Double.isFinite(acceleration[i])) {
                continue;
            }
            if (fast[i] > 0 && acceleration[i] > 0) {
                result[i] = POSITIVE_RISING;
            } else if (fast[i] > 0 && acceleration[i] < 0) {
                result[i] = POSITIVE_FALLING;
            } else if (fast[i] < 0 && acceleration[i] < 0) {
                result[i] = NEGATIVE_FALLING;
            } else if (fast[i] < 0 && acceleration[i] > 0) {
                result[i] = NEGATIVE_RISING;
            } else {
                result[i] = NEUTRAL;
            }
        }
        return result;
    }

    private static double[] high(BarSeries series) {
        double[] values = new double[series.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = series.high(i);
        }
        return values;
    }

    private static double[] low(BarSeries series) {
        double[] values = new double[series.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = series.low(i);
        }
        return values;
    }

    private static double[] close(BarSeries series) {
        double[] values = new double[series.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = series.close(i);
        }
        return values;
    }
}
