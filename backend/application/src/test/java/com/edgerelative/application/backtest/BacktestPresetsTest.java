package com.edgerelative.application.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.application.BacktestPresets;
import com.edgerelative.application.strategy.domain.SetupFamily;
import org.junit.jupiter.api.Test;

class BacktestPresetsTest {

    @Test
    void exposesVersionedResearchPresets() {
        var strategy = BacktestPresets.strategy(BacktestPresets.STRATEGY_RS_RESEARCH).orElseThrow();
        assertThat(strategy.parameterSetId()).isEqualTo(BacktestPresets.STRATEGY_RS_RESEARCH);
        assertThat(strategy.parameterVersion()).isEqualTo(1);
        assertThat(strategy.enabledFamilies()).contains(SetupFamily.M5_3_8_CONFIRMATION);

        var permissive = BacktestPresets.risk(BacktestPresets.RISK_RESEARCH_PERMISSIVE).orElseThrow();
        var conservative = BacktestPresets.risk(BacktestPresets.RISK_RESEARCH_CONSERVATIVE).orElseThrow();
        assertThat(permissive.code()).isEqualTo(BacktestPresets.RISK_RESEARCH_PERMISSIVE);
        assertThat(permissive.trade().baseRiskFraction())
                .isGreaterThan(conservative.trade().baseRiskFraction());
        // Research presets are explicitly not production-calibrated.
        assertThat(permissive.lifecycleState().productionCalibrated()).isFalse();
    }

    @Test
    void unknownPresetsAreEmptySoRunsFailClosedWithAReason() {
        assertThat(BacktestPresets.strategy("NOPE")).isEmpty();
        assertThat(BacktestPresets.risk("NOPE")).isEmpty();
        assertThat(BacktestPresets.strategy(null)).isEmpty();
        assertThat(BacktestPresets.risk(null)).isEmpty();
    }
}
