package com.edgerelative.application.feature.context;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.engine.BarQuality;
import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.math.Atr;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.math.DirectionalEfficiency;
import com.edgerelative.application.feature.math.PriceStructure;
import com.edgerelative.application.feature.policy.FeaturePolicy;

/**
 * Broad-market context: volatility, directional efficiency and confirmed price structure (DD-02
 * §24/§25, DD-05 §142). It intentionally does not emit a market-regime label; DD-02 leaves the regime
 * combination thresholds to research.
 */
public final class MarketContextFeature {

    public record Result(Metric[] atr, Metric[] directionalEfficiency, Metric[] priceStructure) {
    }

    public Result compute(BarSeries market, FeaturePolicy policy, String timeframe) {
        int size = market.size();
        double[] highs = high(market);
        double[] lows = low(market);
        double[] closes = close(market);
        double[] atr = Atr.series(
                highs, lows, closes, policy.atr().lengthFor(timeframe), policy.atr().smoothing());
        Metric[] atrMetrics = new Metric[size];
        Metric[] efficiency = new Metric[size];
        Metric[] structure = new Metric[size];
        for (int i = 0; i < size; i++) {
            FeatureQuality quality = BarQuality.quality(market, i);
            if (!market.complete(i)) {
                atrMetrics[i] = Metric.unavailable(FeatureAvailability.INCOMPLETE, quality, "incomplete bar");
                efficiency[i] = Metric.unavailable(FeatureAvailability.INCOMPLETE, quality, "incomplete bar");
                structure[i] = Metric.unavailable(FeatureAvailability.INCOMPLETE, quality, "incomplete bar");
                continue;
            }
            atrMetrics[i] = Double.isFinite(atr[i]) ? Metric.numeric(atr[i], quality) : Metric.warmingUp();
            double efficiencyValue = DirectionalEfficiency.compute(
                    closes, i + 1, policy.structure().efficiencyWindow());
            efficiency[i] = Double.isFinite(efficiencyValue)
                    ? Metric.numeric(efficiencyValue, quality)
                    : Metric.warmingUp();
            PriceStructure.Result classification =
                    PriceStructure.classify(highs, lows, i + 1, policy.structure().pivotWidth());
            structure[i] = classification.sufficient()
                    ? Metric.label(classification.state(), quality)
                    : Metric.unavailable(FeatureAvailability.INSUFFICIENT_HISTORY, FeatureQuality.INCOMPLETE, "pivots");
        }
        return new Result(atrMetrics, efficiency, structure);
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
