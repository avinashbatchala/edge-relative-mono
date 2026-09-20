package com.edgerelative.application.backtest.application;

import com.edgerelative.application.risk.domain.PolicyState;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.risk.domain.TradingMode;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit, versioned research presets for backtests. These are NOT production defaults: production
 * strategy/risk parameters are promoted separately through the control tables. A preset is recorded
 * in the immutable run spec so a run is reproducible and its assumptions are inspectable.
 */
public final class BacktestPresets {

    public static final String STRATEGY_RS_RESEARCH = "ER_RS_CONTINUATION_V1_RESEARCH";
    public static final String RISK_RESEARCH_PERMISSIVE = "RESEARCH_PERMISSIVE";
    public static final String RISK_RESEARCH_CONSERVATIVE = "RESEARCH_CONSERVATIVE";

    private BacktestPresets() {
    }

    public static Optional<StrategyParameters> strategy(String preset) {
        if (preset == null || preset.isBlank() || !STRATEGY_RS_RESEARCH.equals(preset)) {
            return Optional.empty();
        }
        return Optional.of(new StrategyParameters(
                STRATEGY_RS_RESEARCH,
                1,
                // Only the fully-specified 3/8 family: compression and horizontal are
                // implemented-but-disabled until their research parameters are calibrated.
                Set.of(SetupFamily.M5_3_8_CONFIRMATION),
                0.0, 0.0,
                1.0, 1.0, 1.0,
                5_000_000,
                0.10,
                0.50,
                0.25,
                0.0,
                1,
                3,
                12,
                15,
                15,
                900.0,
                StrategyParameters.NeutralMarketPolicy.BLOCK,
                0.0,
                null, null, null, null, null));
    }

    public static Optional<RiskPolicy> risk(String preset) {
        if (preset == null || preset.isBlank()) {
            return Optional.empty();
        }
        return switch (preset) {
            case RISK_RESEARCH_PERMISSIVE -> Optional.of(policy(preset, dec("0.02"), dec("0.10")));
            case RISK_RESEARCH_CONSERVATIVE -> Optional.of(policy(preset, dec("0.005"), dec("0.03")));
            default -> Optional.empty();
        };
    }

    private static RiskPolicy policy(String code, BigDecimal baseRiskFraction, BigDecimal sessionBudget) {
        return new RiskPolicy(
                code, 1, PolicyState.EXPERIMENTAL, EnumSet.allOf(TradingMode.class), Map.of(),
                new RiskPolicy.TradeLimits(baseRiskFraction, null, null, null, dec("0.25")),
                new RiskPolicy.PortfolioLimits(
                        dec("0.10"), dec("0.15"), dec("2.0"), dec("1.0"), sessionBudget, dec("0.10"), 10),
                new RiskPolicy.SymbolLimits(dec("0.25"), dec("0.05"), dec("0.05"), dec("0.02")),
                new RiskPolicy.SectorLimits(dec("0.50"), dec("0.10"), dec("0.50"), dec("0.10"), false),
                new RiskPolicy.MarginLimits(dec("0.10")),
                new RiskPolicy.LiquidityLimits(dec("0.05"), dec("1000"), false, false, false),
                new RiskPolicy.ExecutionAssumptions(dec("1"), dec("1"), dec("0")),
                new RiskPolicy.StressAssumptions(dec("0.03"), dec("0"), dec("0"), false),
                new RiskPolicy.DrawdownLimits(
                        dec("0.01"), dec("0.02"), dec("0.03"), dec("0.05"), dec("0.08"), dec("0.10"), 20),
                Map.of(com.edgerelative.application.risk.domain.RiskState.REDUCED_1, dec("0.5"),
                        com.edgerelative.application.risk.domain.RiskState.REDUCED_2, dec("0.25")),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                false,
                Map.of(),
                false,
                false,
                false,
                BigDecimal.ONE);
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
