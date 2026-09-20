package com.edgerelative.application.feature.context;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.engine.BarQuality;
import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.math.BarSeries;
import com.edgerelative.application.feature.math.DirectionalEfficiency;
import com.edgerelative.application.feature.math.PriceStructure;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.relative.RrsFeature;
import com.edgerelative.application.feature.relative.RrsParameters;

/**
 * Sector context: sector strength vs broad market using the same RRS formula, plus sector efficiency
 * and price structure (DD-02 §41, DD-05 §150/§173). It never introduces a second relative-strength
 * definition.
 */
public final class SectorContextFeature {

    public record Result(Metric[] rrsRaw, Metric[] directionalEfficiency, Metric[] priceStructure) {
    }

    public Result compute(BarSeries sector, BarSeries market, FeaturePolicy policy, String timeframe) {
        int size = sector.size();
        RrsParameters rrsParameters = new RrsParameters(
                policy.atr().lengthFor(timeframe),
                policy.atr().smoothing(),
                policy.rrs().priceChange(),
                policy.rrs().fastLength(),
                policy.rrs().slowLength(),
                policy.rrs().persistenceWindow(),
                policy.rrs().slopeLookback(),
                policy.rrs().percentileWindow());
        RrsFeature.Result rrs = new RrsFeature().compute(sector, market, rrsParameters);
        double[] highs = array(sector, true, false, false);
        double[] lows = array(sector, false, true, false);
        double[] closes = array(sector, false, false, true);
        Metric[] rrsMetrics = new Metric[size];
        Metric[] efficiency = new Metric[size];
        Metric[] structure = new Metric[size];
        for (int i = 0; i < size; i++) {
            FeatureQuality quality = BarQuality.quality(sector, i);
            if (rrs.rawAvailability()[i] == FeatureAvailability.VALID) {
                rrsMetrics[i] = Metric.numeric(rrs.raw()[i], rrs.rawQuality()[i]);
            } else {
                rrsMetrics[i] = Metric.unavailable(
                        rrs.rawAvailability()[i], rrs.rawQuality()[i], "sector alignment");
            }
            if (!sector.complete(i)) {
                efficiency[i] = Metric.unavailable(FeatureAvailability.INCOMPLETE, quality, "incomplete bar");
                structure[i] = Metric.unavailable(FeatureAvailability.INCOMPLETE, quality, "incomplete bar");
                continue;
            }
            double efficiencyValue =
                    DirectionalEfficiency.compute(closes, i + 1, policy.structure().efficiencyWindow());
            efficiency[i] = Double.isFinite(efficiencyValue)
                    ? Metric.numeric(efficiencyValue, quality)
                    : Metric.warmingUp();
            PriceStructure.Result classification =
                    PriceStructure.classify(highs, lows, i + 1, policy.structure().pivotWidth());
            structure[i] = classification.sufficient()
                    ? Metric.label(classification.state(), quality)
                    : Metric.unavailable(FeatureAvailability.INSUFFICIENT_HISTORY, FeatureQuality.INCOMPLETE, "pivots");
        }
        return new Result(rrsMetrics, efficiency, structure);
    }

    private static double[] array(BarSeries series, boolean high, boolean low, boolean close) {
        double[] values = new double[series.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = high ? series.high(i) : low ? series.low(i) : series.close(i);
        }
        return values;
    }
}
