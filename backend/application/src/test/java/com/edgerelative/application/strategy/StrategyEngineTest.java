package com.edgerelative.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.strategy.domain.DependencyStatus;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.GateCode;
import com.edgerelative.application.strategy.domain.GateStatus;
import com.edgerelative.application.strategy.domain.HardGateResult;
import com.edgerelative.application.strategy.domain.ReasonCode;
import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.SetupInitialization;
import com.edgerelative.application.strategy.domain.SetupState;
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
import com.edgerelative.application.strategy.domain.StrategyEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StrategyEngineTest {

    private static final StrategyEngine ENGINE = new StrategyEngine(SetupFamilyRegistry.production());
    private static final Instant T = Instant.parse("2026-09-18T04:30:00Z");
    private static final LocalDate SESSION = LocalDate.of(2026, 9, 18);

    private static StrategyParameters parameters() {
        return new StrategyParameters(
                "TEST_PARAMS",
                1,
                Set.of(SetupFamily.M5_3_8_CONFIRMATION),
                0.5, 0.5,
                1.2, 1.5, 1.2,
                5_000_000,
                0.5,
                0.8,
                0.25,
                0.05,
                1,
                12,
                12,
                15,
                15,
                300.0,
                StrategyParameters.NeutralMarketPolicy.BLOCK,
                0.0,
                null, null, null, null, null);
    }

    private static MarketContext market(String bias) {
        return new MarketContext(bias, "RANGE", "BULL_IMPULSE", T, true);
    }

    private static StockContext stock() {
        return new StockContext(
                "LONG_ALIGNED",
                1.2, 0.8, 0.6, 0.5, 0.9, "POSITIVE_RISING",
                1.3, 1.8, 1.4, 0.2,
                1.5, 100.0, 0.05,
                "VALID", 10_000_000.0, 5.0,
                1.0, false,
                new StructureContext(
                        false, null, null, null, null,
                        false, null,
                        true, new BigDecimal("100.4"), new BigDecimal("100.0"),
                        new BigDecimal("99.9"), new BigDecimal("100.0")));
    }

    private static CompletedCandle candle() {
        return new CompletedCandle(
                T.minusSeconds(300), T, new BigDecimal("100.3"), new BigDecimal("100.6"),
                new BigDecimal("100.1"), new BigDecimal("100.5"), 10_000);
    }

    private static StrategyEvaluationInput input(
            MarketContext market, StockContext stock, List<DependencyStatus> deps, PriorSetup prior) {
        return new StrategyEvaluationInput(
                "eval-1",
                T,
                SESSION,
                "ER_RS_CONTINUATION_V1/v1",
                "TEST_PARAMS",
                1L,
                10L,
                "mo-1",
                new SessionContext(T, true, true, false, false, "nse-session-v1"),
                market,
                null,
                stock,
                candle(),
                deps,
                prior);
    }

    private static StrategyEvaluationInput validLong() {
        return input(market("BULLISH"), stock(), List.of(), PriorSetup.none());
    }

    @Test
    void validLongThreeEightCrossReachesValid() {
        StrategyEvaluationResult result = ENGINE.evaluate(validLong(), parameters(), Direction.LONG);
        assertThat(result.setupState()).isEqualTo(SetupState.VALID);
        assertThat(result.valid()).isTrue();
        assertThat(result.setupFamily()).isEqualTo(SetupFamily.M5_3_8_CONFIRMATION);
        assertThat(result.trigger()).isNotNull();
        assertThat(result.invalidation()).isNotNull();
    }

    @Test
    void shortIsEvaluatedSymmetrically() {
        StockContext shortStock = new StockContext(
                "SHORT_ALIGNED",
                -1.2, -0.8, -0.6, -0.5, 0.9, "NEGATIVE_FALLING",
                1.3, 1.8, 1.4, 0.2,
                1.5, 100.0, 0.05,
                "VALID", 10_000_000.0, 5.0,
                1.0, false,
                new StructureContext(
                        false, null, null, null, null,
                        false, null,
                        true, new BigDecimal("99.6"), new BigDecimal("100.0"),
                        new BigDecimal("100.1"), new BigDecimal("100.0")));
        StrategyEvaluationResult result = ENGINE.evaluate(
                input(market("BEARISH"), shortStock, List.of(), PriorSetup.none()), parameters(), Direction.SHORT);
        assertThat(result.setupState()).isEqualTo(SetupState.VALID);
        assertThat(result.valid()).isTrue();
    }

    @Test
    void persistentBearishRelativeWeaknessQualifiesForShort() {
        // Persistence is a magnitude in [0, 1] independent of direction: a strongly persistent
        // bearish stock (RRS raw < 0, persistence near 1.0) must pass the M5 gate, not be rejected.
        StrategyEvaluationResult result = ENGINE.evaluate(
                input(market("BEARISH"), shortStock(1.0), List.of(), PriorSetup.none()), parameters(), Direction.SHORT);
        assertThat(result.hardGates())
                .filteredOn(gate -> gate.gateCode() == GateCode.RRS_M5_PERSISTENCE)
                .allSatisfy(gate -> assertThat(gate.status()).isEqualTo(GateStatus.PASSED));
    }

    @Test
    void lowPersistenceShortIsRejectedByTheM5Gate() {
        StrategyEvaluationResult result = ENGINE.evaluate(
                input(market("BEARISH"), shortStock(0.2), List.of(), PriorSetup.none()), parameters(), Direction.SHORT);
        assertThat(result.hardGates())
                .filteredOn(gate -> gate.gateCode() == GateCode.RRS_M5_PERSISTENCE)
                .allSatisfy(gate -> assertThat(gate.status()).isEqualTo(GateStatus.FAILED));
        assertThat(result.reasonCodes()).contains(ReasonCode.RRS_M5_FAILED);
    }

    private static StockContext shortStock(double persistence) {
        return new StockContext(
                "SHORT_ALIGNED",
                -1.2, -0.8, -0.6, -0.5, persistence, "NEGATIVE_FALLING",
                1.3, 1.8, 1.4, 0.2,
                1.5, 100.0, 0.05,
                "VALID", 10_000_000.0, 5.0,
                1.0, false,
                new StructureContext(
                        false, null, null, null, null,
                        false, null,
                        true, new BigDecimal("99.6"), new BigDecimal("100.0"),
                        new BigDecimal("100.1"), new BigDecimal("100.0")));
    }

    @Test
    void opposingMarketPreventsValid() {
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BEARISH"), stock(), List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.MARKET_OPPOSING);
    }

    @Test
    void lowRelativeVolumePreventsValid() {
        StockContext low = withRvol(stock(), 0.5, 0.5, 0.5);
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), low, List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.RVOL_FAILED);
    }

    @Test
    void invalidLiquidityPreventsValid() {
        StockContext illiquid = new StockContext(
                "LONG_ALIGNED", 1.2, 0.8, 0.6, 0.5, 0.9, "POSITIVE_RISING",
                1.3, 1.8, 1.4, 0.2, 1.5, 100.0, 0.05,
                "VALID", 1_000.0, 5.0, 1.0, false, stock().structure());
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), illiquid, List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.LIQUIDITY_FAILED);
    }

    @Test
    void insufficientTechnicalVoidPreventsValid() {
        StockContext tight = new StockContext(
                "LONG_ALIGNED", 1.2, 0.8, 0.6, 0.5, 0.9, "POSITIVE_RISING",
                1.3, 1.8, 1.4, 0.2, 1.5, 100.0, 0.05,
                "VALID", 10_000_000.0, 5.0, 0.1, false, stock().structure());
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), tight, List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.NO_TECHNICAL_VOID);
    }

    @Test
    void extendedEntryIsMissedNotValid() {
        StockContext extended = new StockContext(
                "LONG_ALIGNED", 1.2, 0.8, 0.6, 0.5, 0.9, "POSITIVE_RISING",
                1.3, 1.8, 1.4, 0.2, 1.5, 102.0, 0.05, // 2.0 / 1.5 ATR away from 100.0
                "VALID", 10_000_000.0, 5.0, 1.0, false, stock().structure());
        PriorSetup nearTrigger = new PriorSetup(
                java.util.UUID.randomUUID(), SetupState.NEAR_TRIGGER, SetupFamily.M5_3_8_CONFIRMATION,
                Direction.LONG, 2, 0, null, null, null);
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), extended, List.of(), nearTrigger), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.ENTRY_EXTENDED);
        assertThat(result.setupState()).isEqualTo(SetupState.MISSED);
    }

    @Test
    void missingMarketBiasCannotReachValid() {
        MarketContext unavailable = new MarketContext(null, null, null, T, false);
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(unavailable, stock(), List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        HardGateResult bias = gate(result, GateCode.MARKET_BIAS_PERMISSION);
        assertThat(bias.status()).isEqualTo(GateStatus.UNAVAILABLE);
    }

    @Test
    void staleRequiredDependencyReportsDataInvalid() {
        List<DependencyStatus> deps = List.of(new DependencyStatus(
                "MARKET_M5", true, DependencyStatus.DependencyState.STALE));
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), stock(), deps, PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.DATA_INVALID);
    }

    @Test
    void unconfirmedTriggerIsNearTriggerNotValid() {
        StockContext near = new StockContext(
                "LONG_ALIGNED", 1.2, 0.8, 0.6, 0.5, 0.9, "POSITIVE_RISING",
                1.3, 1.8, 1.4, 0.2, 1.5, 100.1, 0.05,
                "VALID", 10_000_000.0, 5.0, 1.0, false,
                new StructureContext(false, null, null, null, null, false, null,
                        true, new BigDecimal("99.95"), new BigDecimal("100.0"),
                        new BigDecimal("99.9"), new BigDecimal("100.0")));
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), near, List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.setupState()).isEqualTo(SetupState.NEAR_TRIGGER);
    }

    @Test
    void agedSetupExpires() {
        PriorSetup prior = new PriorSetup(
                java.util.UUID.randomUUID(), SetupState.NEAR_TRIGGER, SetupFamily.M5_3_8_CONFIRMATION,
                Direction.LONG, 20, 0, null, null, null);
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("BULLISH"), stock(), List.of(), prior), parameters(), Direction.LONG);
        assertThat(result.setupState()).isEqualTo(SetupState.EXPIRED);
        assertThat(result.reasonCodes()).contains(ReasonCode.TRIGGER_EXPIRED);
    }

    @Test
    void neutralMarketPolicyBlocksByDefault() {
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(market("NEUTRAL"), stock(), List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.valid()).isFalse();
        assertThat(result.reasonCodes()).contains(ReasonCode.NEUTRAL_MARKET_BLOCKED);
    }

    @Test
    void noneObservationHasNoSetupInstance() {
        MarketContext unavailable = new MarketContext(null, null, null, T, false);
        StrategyEvaluationResult result =
                ENGINE.evaluate(input(unavailable, stock(), List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.setupState()).isEqualTo(SetupState.NONE);
        assertThat(result.setupInstanceId()).isNull();
        assertThat(result.initialization()).isNull();
    }

    @Test
    void coldStartAboveWatchIsReconstructionAndStillRequiresEveryGate() {
        StrategyEvaluationResult valid = ENGINE.evaluate(validLong(), parameters(), Direction.LONG);
        assertThat(valid.setupState()).isEqualTo(SetupState.VALID);
        assertThat(valid.setupInstanceId()).isNotNull();
        assertThat(valid.initialization()).isEqualTo(SetupInitialization.COLD_START_RECONSTRUCTION);

        // A cold start must never skip qualification: break one required gate and it is not VALID.
        StockContext low = withRvol(stock(), 0.5, 0.5, 0.5);
        StrategyEvaluationResult notValid =
                ENGINE.evaluate(input(market("BULLISH"), low, List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(notValid.valid()).isFalse();
        assertThat(notValid.setupState()).isNotEqualTo(SetupState.VALID);
    }

    @Test
    void watchEntryIsLifecycleInitialization() {
        StockContext noStructure = new StockContext(
                "LONG_ALIGNED", 1.2, 0.8, 0.6, 0.5, 0.9, "POSITIVE_RISING",
                1.3, 1.8, 1.4, 0.2, 1.5, 100.0, 0.05,
                "VALID", 10_000_000.0, 5.0, 1.0, false, StructureContext.empty());
        StrategyEvaluationResult result = ENGINE.evaluate(
                input(market("BULLISH"), noStructure, List.of(), PriorSetup.none()), parameters(), Direction.LONG);
        assertThat(result.setupState()).isEqualTo(SetupState.WATCH);
        assertThat(result.setupInstanceId()).isNotNull();
        assertThat(result.initialization()).isEqualTo(SetupInitialization.LIFECYCLE_START);
    }

    @Test
    void setupInstanceIsDerivedDeterministicallyAndCarriedForward() {
        StrategyEvaluationResult first = ENGINE.evaluate(validLong(), parameters(), Direction.LONG);
        StrategyEvaluationResult second = ENGINE.evaluate(validLong(), parameters(), Direction.LONG);
        assertThat(first.setupInstanceId()).isNotNull();
        assertThat(second.setupInstanceId()).isEqualTo(first.setupInstanceId());

        java.util.UUID instance = first.setupInstanceId();
        PriorSetup carried = new PriorSetup(
                instance, SetupState.FORMING, SetupFamily.M5_3_8_CONFIRMATION,
                Direction.LONG, 3, 0, null, null, null);
        StrategyEvaluationResult next = ENGINE.evaluate(
                input(market("BULLISH"), stock(), List.of(), carried), parameters(), Direction.LONG);
        assertThat(next.setupInstanceId()).isEqualTo(instance);
    }

    @Test
    void reusedInputIsDeterministic() {
        StrategyEvaluationResult first = ENGINE.evaluate(validLong(), parameters(), Direction.LONG);
        StrategyEvaluationResult second = ENGINE.evaluate(validLong(), parameters(), Direction.LONG);
        assertThat(first).isEqualTo(second);
    }

    private static HardGateResult gate(StrategyEvaluationResult result, GateCode code) {
        Optional<HardGateResult> gate = result.hardGates().stream()
                .filter(candidate -> candidate.gateCode() == code)
                .findFirst();
        assertThat(gate).isPresent();
        return gate.get();
    }

    private static StockContext withRvol(StockContext base, double daily, double interval, double cumulative) {
        return new StockContext(
                base.dailyStructure(), base.rrsD1(), base.rrsM5Raw(), base.rrsM5Fast(), base.rrsM5Slow(),
                base.rrsM5Persistence(), base.rrsTrendState(),
                daily, interval, cumulative, base.rve(), base.atrM5(), base.lastPrice(), base.tickSize(),
                base.liquidityState(), base.medianTradedValue(), base.spreadBps(), base.technicalVoidAtr(),
                base.eventRiskBlocked(), base.structure());
    }
}
