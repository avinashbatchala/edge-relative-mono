package com.edgerelative.application.strategy.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.strategy.context.DecisionContextAssembler.EmaStructure;
import com.edgerelative.application.strategy.context.DecisionContextAssembler.EmaTracker;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionContextAssemblerTest {

    @Test
    void emaTrackerIsAbsentUntilSeededThenReportsTheCrossover() {
        EmaTracker tracker = new EmaTracker(3, 8);
        List<BigDecimal> closes = new ArrayList<>();
        // Eight flat bars seed the EMA without a crossover.
        for (int i = 0; i < 8; i++) {
            closes.add(new BigDecimal("100.0"));
        }
        // A down leg pushes EMA3 below EMA8, then an up leg crosses it back above.
        for (int i = 0; i < 6; i++) {
            closes.add(new BigDecimal("99.0").subtract(new BigDecimal(i).multiply(new BigDecimal("0.10"))));
        }
        for (int i = 0; i < 6; i++) {
            closes.add(new BigDecimal("99.0").add(new BigDecimal(i).multiply(new BigDecimal("0.20"))));
        }

        boolean sawAbsent = false;
        boolean sawCross = false;
        EmaStructure previous = null;
        for (BigDecimal close : closes) {
            EmaStructure current = tracker.update(close);
            if (!current.present()) {
                sawAbsent = true;
            }
            if (previous != null && previous.present() && current.present()) {
                if (previous.ema3().compareTo(previous.ema8()) <= 0
                        && current.ema3().compareTo(current.ema8()) > 0) {
                    sawCross = true;
                }
            }
            previous = current;
        }

        assertThat(sawAbsent).isTrue();
        assertThat(previous.present()).isTrue();
        assertThat(sawCross).isTrue();
    }

    @Test
    void strictPolicyLeavesEventRiskUnknownWhileResearchAssumesClear() {
        assertThat(DecisionContextPolicy.strict().assumeEventRiskClear()).isFalse();
        assertThat(DecisionContextPolicy.research().assumeEventRiskClear()).isTrue();
    }
}
