package com.edgerelative.application.risk.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, versioned risk policy (DD-03 §179–§181). Every numeric value is a research parameter:
 * none is invented here, and a null value means "not configured", which the evaluator treats as a
 * blocker rather than a default. Synthetic fixtures may populate values; production resolution must
 * supply a calibrated {@link PolicyState#productionCalibrated()} version.
 */
public record RiskPolicy(
        String code,
        int version,
        PolicyState lifecycleState,
        Set<TradingMode> allowedModes,
        Map<TradingMode, PolicyState> minimumStateForMode,
        TradeLimits trade,
        PortfolioLimits portfolio,
        SymbolLimits symbol,
        SectorLimits sector,
        MarginLimits margin,
        LiquidityLimits liquidity,
        ExecutionAssumptions execution,
        StressAssumptions stress,
        DrawdownLimits drawdown,
        Map<RiskState, BigDecimal> stateModifiers,
        Map<String, BigDecimal> strategyRiskFractions,
        Map<String, BigDecimal> strategyRiskBudgetFractions,
        Map<String, BigDecimal> symbolRiskBudgetFractions,
        Map<String, BigDecimal> sectorRiskBudgetFractions,
        boolean correlationEnabled,
        Map<String, BigDecimal> correlationModifiers,
        boolean averagingDownEnabled,
        boolean pyramidingEnabled,
        boolean qualitySizingEnabled,
        BigDecimal mlRiskModifier) {

    public RiskPolicy {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("risk policy code is required");
        }
        if (lifecycleState == null) {
            throw new IllegalArgumentException("risk policy lifecycle state is required");
        }
        allowedModes = allowedModes == null ? Set.of() : Set.copyOf(allowedModes);
        minimumStateForMode = minimumStateForMode == null ? Map.of() : Map.copyOf(minimumStateForMode);
        stateModifiers = stateModifiers == null ? Map.of() : Map.copyOf(stateModifiers);
        strategyRiskFractions = strategyRiskFractions == null ? Map.of() : Map.copyOf(strategyRiskFractions);
        strategyRiskBudgetFractions =
                strategyRiskBudgetFractions == null ? Map.of() : Map.copyOf(strategyRiskBudgetFractions);
        symbolRiskBudgetFractions =
                symbolRiskBudgetFractions == null ? Map.of() : Map.copyOf(symbolRiskBudgetFractions);
        sectorRiskBudgetFractions =
                sectorRiskBudgetFractions == null ? Map.of() : Map.copyOf(sectorRiskBudgetFractions);
        correlationModifiers = correlationModifiers == null ? Map.of() : Map.copyOf(correlationModifiers);
        if (mlRiskModifier != null && mlRiskModifier.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("ML risk modifier must be 1.0 while ML authority is disabled");
        }
    }

    /** Copy the policy with an execution minimum-stop floor applied (research override). */
    public RiskPolicy withMinStopAtr(BigDecimal minStopAtr) {
        ExecutionAssumptions updated = execution == null
                ? null
                : new ExecutionAssumptions(
                        execution.adverseSlippageTicks(), execution.exitCostTicks(), execution.exitCostBps(), minStopAtr);
        return new RiskPolicy(
                code, version, lifecycleState, allowedModes, minimumStateForMode, trade, portfolio, symbol, sector,
                margin, liquidity, updated, stress, drawdown, stateModifiers, strategyRiskFractions,
                strategyRiskBudgetFractions, symbolRiskBudgetFractions, sectorRiskBudgetFractions, correlationEnabled,
                correlationModifiers, averagingDownEnabled, pyramidingEnabled, qualitySizingEnabled, mlRiskModifier);
    }

    public RiskStateMultiplier stateModifier(RiskState state) {        if (state == RiskState.NORMAL) {
            return RiskStateMultiplier.ONE;
        }
        BigDecimal modifier = stateModifiers.get(state);
        if (modifier == null) {
            return null;
        }
        if (modifier.signum() <= 0 || modifier.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("risk-state modifier must satisfy 0 < m <= 1 for " + state);
        }
        return new RiskStateMultiplier(modifier);
    }

    public boolean appliesTo(TradingMode mode) {
        if (!allowedModes.contains(mode)) {
            return false;
        }
        PolicyState required = minimumStateForMode.get(mode);
        return required == null || lifecycleState.atLeast(required);
    }

    public record RiskStateMultiplier(BigDecimal value) {
        public static final RiskStateMultiplier ONE = new RiskStateMultiplier(BigDecimal.ONE);
    }

    public record TradeLimits(
            BigDecimal baseRiskFraction,
            BigDecimal absoluteRiskCap,
            BigDecimal strategyCeilingFraction,
            BigDecimal deploymentStageCeilingFraction,
            BigDecimal maxPositionNotionalFraction) {
    }

    public record PortfolioLimits(
            BigDecimal maxOpenRiskFraction,
            BigDecimal maxStressRiskFraction,
            BigDecimal maxGrossExposureFraction,
            BigDecimal maxNetExposureFraction,
            BigDecimal sessionRiskBudgetFraction,
            BigDecimal portfolioRiskBudgetFraction,
            Integer maxPositions) {
    }

    public record SymbolLimits(
            BigDecimal maxNotionalFraction,
            BigDecimal maxOpenRiskFraction,
            BigDecimal riskBudgetFraction,
            BigDecimal maxSessionLossFraction) {
    }

    public record SectorLimits(
            BigDecimal maxNotionalFraction,
            BigDecimal maxOpenRiskFraction,
            BigDecimal maxDirectionalRiskFraction,
            BigDecimal riskBudgetFraction,
            boolean sectorMappingMandatory) {
    }

    public record MarginLimits(BigDecimal safetyBufferFraction) {
    }

    public record LiquidityLimits(
            BigDecimal maxParticipationFraction,
            BigDecimal maxSpreadBps,
            boolean liquidityMandatory,
            boolean brokerCapMandatory,
            boolean spreadMandatory) {
    }

    /**
     * Execution planning assumptions. {@code minStopAtr} is an optional research safety floor: the
     * protective stop is placed at least this many ATRs from entry (never moving the strategy's
     * structural invalidation), which re-sizes quantity so risk stays authoritative.
     */
    public record ExecutionAssumptions(
            BigDecimal adverseSlippageTicks,
            BigDecimal exitCostTicks,
            BigDecimal exitCostBps,
            BigDecimal minStopAtr) {
    }

    public record StressAssumptions(
            BigDecimal adverseMoveFraction, BigDecimal costPerUnit, BigDecimal costBps, boolean enabled) {
    }

    public record DrawdownLimits(
            BigDecimal dailyReduce1Fraction,
            BigDecimal dailyReduce2Fraction,
            BigDecimal dailyStopNewFraction,
            BigDecimal weeklyLimitFraction,
            BigDecimal monthlyLimitFraction,
            BigDecimal accountLimitFraction,
            Integer maxConsecutiveLosses) {
    }
}
