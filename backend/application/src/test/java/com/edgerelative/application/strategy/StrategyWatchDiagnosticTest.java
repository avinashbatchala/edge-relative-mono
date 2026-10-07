package com.edgerelative.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.backtest.application.BacktestPresets;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.GateCode;
import com.edgerelative.application.strategy.domain.SetupState;
import com.edgerelative.application.strategy.domain.StrategyEngine;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.CompletedCandle;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.MarketContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.PriorSetup;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.SessionContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.StockContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.StructureContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.domain.family.SetupFamilyRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the real-data failure where no setup ever reached WATCH. The engine
 * age-expiry must apply only to active lifecycle states: a long-running {@code NONE} must not be
 * "expired" (which is an illegal transition from NONE and collapses back to NONE, trapping the state
 * forever).
 */
class StrategyWatchDiagnosticTest {

    private static final Instant T = Instant.parse("2025-07-08T08:00:00Z");
    private static final LocalDate SESSION = LocalDate.of(2025, 7, 8);

    private static StrategyEngine engine() {
        return new StrategyEngine(SetupFamilyRegistry.production());
    }

    private static StrategyParameters parameters() {
        return BacktestPresets.strategy(BacktestPresets.STRATEGY_RS_RESEARCH).orElseThrow();
    }

    private static StockContext stock() {
        return new StockContext(
                "LONG_ALIGNED",
                0.043,
                -0.377,           // rrsM5Raw negative -> long M5 gate fails
                -0.30, -0.20, 0.5,
                "NEGATIVE_FALLING",
                0.6, 0.649, 0.7,  // rvol below the 1.0 preset minimum -> volume fails
                0.1,
                2.57, 100.0, 0.05,
                "VALID", 40_000_000.0, null,
                1.5, Boolean.FALSE,
                new StructureContext(false, null, null, null, null, false, null,
                        true, new BigDecimal("100.40"), new BigDecimal("100.00"),
                        new BigDecimal("99.90"), new BigDecimal("100.00")));
    }

    private static StrategyEvaluationInput input(PriorSetup prior) {
        StrategyParameters parameters = parameters();
        return new StrategyEvaluationInput(
                "eval", T, SESSION, "ER_RS_CONTINUATION_V1/v1", parameters.parameterSetId(),
                1L, 1L, "mo", new SessionContext(T, true, true, false, false, "nse-session-v1"),
                new MarketContext("BULLISH", "TREND", null, T, true), null, stock(),
                new CompletedCandle(T.minusSeconds(300), T, new BigDecimal("100.3"), new BigDecimal("100.6"),
                        new BigDecimal("100.1"), new BigDecimal("100.5"), 10_000),
                List.of(), prior);
    }

    @Test
    void watchConditionsMetButFormingFailsMustStillBeWatch() {
        StrategyEvaluationResult result =
                engine().evaluate(input(PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(status(result, GateCode.DAILY_STRUCTURE_ALIGNED)).isEqualTo("PASSED");
        assertThat(status(result, GateCode.RRS_D1_DIRECTION)).isEqualTo("PASSED");
        assertThat(status(result, GateCode.MARKET_BIAS_PERMISSION)).isEqualTo("PASSED");
        assertThat(status(result, GateCode.LIQUIDITY)).isEqualTo("PASSED");
        assertThat(result.setupState()).isEqualTo(SetupState.WATCH);
    }

    @Test
    void staleNoneCounterMustNotTrapTheStateAtNone() {
        // The setup has been NONE for longer than maxBarsInState (12). It must still be allowed to
        // enter WATCH; age-expiry applies only to active states.
        PriorSetup staleNone = new PriorSetup(
                null, SetupState.NONE, null, null, 21, 0, null, null, null);
        StrategyEvaluationResult result =
                engine().evaluate(input(staleNone), parameters(), Direction.LONG);
        assertThat(result.setupState()).isEqualTo(SetupState.WATCH);
    }

    private static String status(StrategyEvaluationResult result, GateCode code) {
        return result.hardGates().stream()
                .filter(gate -> gate.gateCode() == code)
                .map(gate -> gate.status().name())
                .findFirst()
                .orElse("MISSING");
    }
}
