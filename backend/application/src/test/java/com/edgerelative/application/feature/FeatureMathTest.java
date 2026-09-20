package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.math.Atr;
import com.edgerelative.application.feature.math.AtrSmoothing;
import com.edgerelative.application.feature.math.DirectionalEfficiency;
import com.edgerelative.application.feature.math.Ema;
import com.edgerelative.application.feature.math.PriceStructure;
import org.junit.jupiter.api.Test;

class FeatureMathTest {

    @Test
    void atrIsDefinedOnlyAfterWarmup() {
        double[] high = {10, 11, 12, 13};
        double[] low = {8, 9, 10, 11};
        double[] close = {9, 10, 11, 12};
        double[] atr = Atr.series(high, low, close, 3, AtrSmoothing.WILDER);
        assertThat(atr[0]).isNaN();
        assertThat(atr[1]).isNaN();
        assertThat(atr[2]).isEqualTo(2.0);
        assertThat(atr[3]).isEqualTo(2.0);
        assertThat(Atr.warmupBars(3)).isEqualTo(3);
    }

    @Test
    void atrReturnsAllNaNWithInsufficientHistory() {
        double[] high = {10, 11};
        double[] low = {8, 9};
        double[] close = {9, 10};
        double[] atr = Atr.series(high, low, close, 5, AtrSmoothing.WILDER);
        assertThat(java.util.Arrays.stream(atr).allMatch(Double::isNaN)).isTrue();
    }

    @Test
    void atrAtAnIndexDoesNotChangeWhenFutureBarsAreAppended() {
        double[] high = {10, 11, 12, 13, 20};
        double[] low = {8, 9, 10, 11, 1};
        double[] close = {9, 10, 11, 12, 5};
        double[] shortSeries = Atr.series(new double[]{10, 11, 12}, new double[]{8, 9, 10}, new double[]{9, 10, 11}, 3, AtrSmoothing.WILDER);
        double[] longSeries = Atr.series(high, low, close, 3, AtrSmoothing.WILDER);
        assertThat(shortSeries[2]).isEqualTo(longSeries[2]);
    }

    @Test
    void emaSeedsWithSimpleMeanThenSmooths() {
        double[] values = {1, 2, 3, 4};
        double[] ema = Ema.series(values, 3);
        assertThat(ema[0]).isNaN();
        assertThat(ema[1]).isNaN();
        assertThat(ema[2]).isEqualTo(2.0);
        assertThat(ema[3]).isEqualTo(3.0);
    }

    @Test
    void sparseEmaResumesAfterAGap() {
        double[] values = {1, Double.NaN, 3, 4};
        double[] ema = Ema.seriesSparse(values, 2);
        // first two valid values are 1 and 3 -> seed 2.0 at index 2; then alpha 2/3
        assertThat(ema[2]).isEqualTo(2.0);
        assertThat(ema[3]).isCloseTo(3.3333333333333335, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void directionalEfficiencyIsOneForAPerfectTrendAndZeroForFlat() {
        double[] trend = {1, 2, 3, 4, 5};
        assertThat(DirectionalEfficiency.compute(trend, 4)).isEqualTo(1.0);
        double[] flat = {5, 5, 5, 5, 5};
        assertThat(DirectionalEfficiency.compute(flat, 4)).isEqualTo(0.0);
        double[] chop = {1, 3, 2, 4, 3};
        assertThat(DirectionalEfficiency.compute(chop, 4)).isLessThan(1.0);
    }

    @Test
    void priceStructureClassifiesHigherHighsAndLows() {
        double[] highs = {2, 3, 2, 5, 2, 7, 2};
        double[] lows = {2, 1, 2, 3, 2, 4, 3};
        PriceStructure.Result result = PriceStructure.classify(highs, lows, 1);
        assertThat(result.state()).isEqualTo(PriceStructure.BULL);
    }

    @Test
    void priceStructureRequiresEnoughPivots() {
        double[] highs = {1, 2, 1};
        double[] lows = {0, 1, 0};
        assertThat(PriceStructure.classify(highs, lows, 1).sufficient()).isFalse();
    }
}
