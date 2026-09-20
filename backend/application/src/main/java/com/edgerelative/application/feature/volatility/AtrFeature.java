package com.edgerelative.application.feature.volatility;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.engine.BarQuality;
import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.math.Atr;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.BarSeries;

/**
 * ATR as a first-class versioned feature/dependency (DD-02 §34, DD-05 §144). It delegates to the one
 * canonical {@link Atr} implementation so no second volatility formula can appear.
 */
public final class AtrFeature {

    public double[] raw(BarSeries series, int length, AtrSmoothing smoothing) {
        double[] high = new double[series.size()];
        double[] low = new double[series.size()];
        double[] close = new double[series.size()];
        for (int i = 0; i < series.size(); i++) {
            high[i] = series.high(i);
            low[i] = series.low(i);
            close[i] = series.close(i);
        }
        return Atr.series(high, low, close, length, smoothing);
    }

    public Metric metricAt(BarSeries series, int index, double[] atr) {
        FeatureQuality quality = BarQuality.quality(series, index);
        if (!series.complete(index)) {
            return Metric.unavailable(FeatureAvailability.INCOMPLETE, quality, "incomplete bar");
        }
        if (!Double.isFinite(atr[index])) {
            return Metric.warmingUp();
        }
        return Metric.numeric(atr[index], quality);
    }
}
