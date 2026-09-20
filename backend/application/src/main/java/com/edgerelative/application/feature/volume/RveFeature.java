package com.edgerelative.application.feature.volume;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.math.Ema;

/**
 * RVE = Relative Volume Expansion (DD-02 §50).
 *
 * <p>{@code fast = EWMA(log(RVOL), fast)}, {@code slow = EWMA(log(RVOL), slow)},
 * {@code RVE = fast - slow}. Built directly from the interval RVOL series so there is one RVOL
 * trajectory and no second volume definition.
 */
public final class RveFeature {

    public Metric[] compute(Metric[] intervalRvol, int fastLength, int slowLength) {
        int size = intervalRvol.length;
        double[] logRvol = new double[size];
        for (int i = 0; i < size; i++) {
            Metric metric = intervalRvol[i];
            if (metric.available() && metric.value() > 0.0) {
                logRvol[i] = Math.log(metric.value());
            } else {
                logRvol[i] = Double.NaN;
            }
        }
        double[] fast = Ema.seriesSparse(logRvol, fastLength);
        double[] slow = Ema.seriesSparse(logRvol, slowLength);
        Metric[] result = new Metric[size];
        for (int i = 0; i < size; i++) {
            if (!intervalRvol[i].available()) {
                result[i] = Metric.unavailable(
                        intervalRvol[i].availability(),
                        intervalRvol[i].quality(),
                        "interval RVOL unavailable");
            } else if (!Double.isFinite(fast[i]) || !Double.isFinite(slow[i])) {
                result[i] = Metric.unavailable(
                        FeatureAvailability.WARMING_UP, FeatureQuality.INCOMPLETE, "RVE warmup");
            } else {
                result[i] = Metric.numeric(fast[i] - slow[i], intervalRvol[i].quality());
            }
        }
        return result;
    }
}
