package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.engine.Metric;
import com.edgerelative.application.feature.volume.RveFeature;
import org.junit.jupiter.api.Test;

class RveFeatureTest {

    private static Metric[] rvol(double... values) {
        Metric[] metrics = new Metric[values.length];
        for (int i = 0; i < values.length; i++) {
            metrics[i] = values[i] > 0 ? Metric.numeric(values[i]) : Metric.warmingUp();
        }
        return metrics;
    }

    @Test
    void stableRvolProducesApproximatelyZero() {
        Metric[] result = new RveFeature().compute(rvol(1.0, 1.0, 1.0, 1.0), 1, 2);
        assertThat(result[1].value()).isEqualTo(0.0);
        assertThat(result[2].value()).isEqualTo(0.0);
        assertThat(result[3].value()).isEqualTo(0.0);
    }

    @Test
    void expandingRvolIsPositiveAndContractingIsNegative() {
        Metric[] expanding = new RveFeature().compute(rvol(1, 1, 1, 4), 1, 2);
        assertThat(expanding[3].value()).isPositive();
        Metric[] contracting = new RveFeature().compute(rvol(4, 4, 4, 1), 1, 2);
        assertThat(contracting[3].value()).isNegative();
    }

    @Test
    void usesLogEwmaSemantics() {
        Metric[] result = new RveFeature().compute(rvol(1, 1, 1, 4), 1, 2);
        // fast = log(4); slow = 2/3*log(4); rve = 1/3*log(4)
        assertThat(result[3].value()).isCloseTo(Math.log(4) / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void insufficientWarmupIsUnavailable() {
        Metric[] result = new RveFeature().compute(rvol(1.0), 3, 8);
        assertThat(result[0].available()).isFalse();
    }
}
