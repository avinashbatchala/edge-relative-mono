package com.edgerelative.application.tradeplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edgerelative.application.risk.domain.PolicyState;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.risk.domain.RiskDecisionType;
import com.edgerelative.application.risk.domain.RiskReasonCode;
import com.edgerelative.application.risk.domain.RiskState;
import com.edgerelative.application.risk.domain.TradingMode;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.tradeplan.domain.PlanEligibility;
import com.edgerelative.application.tradeplan.domain.PlanLineage;
import com.edgerelative.application.tradeplan.domain.TradePlan;
import com.edgerelative.application.tradeplan.domain.TradePlanEligibilityEvaluator;
import com.edgerelative.application.tradeplan.domain.TradePlanException;
import com.edgerelative.application.tradeplan.domain.TradePlanFactory;
import com.edgerelative.application.tradeplan.domain.TradePlanPolicy;
import com.edgerelative.application.tradeplan.domain.TradePlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradePlanFactoryTest {

    private static final Instant T = Instant.parse("2026-09-18T04:30:00Z");
    private static final Instant CLOSE = Instant.parse("2026-09-18T10:00:00Z");

    private static TradePlanPolicy policy() {
        return new TradePlanPolicy(
                "ER_TRADE_PLAN_V1_SYNTHETIC", 1, "trade-plan-policy/v1", 30, 15,
                new BigDecimal("2"), "REFERENCE_PRICE", "STRUCTURAL_UNRESOLVED", null, "TICK_BUFFER");
    }

    private static PlanLineage lineage(Direction direction, BigDecimal trigger) {
        boolean longSide = direction.isLong();
        return new PlanLineage(
                42, "11111111-1111-1111-1111-111111111111", "M5_3_8_CONFIRMATION", "VALID",
                1, 1, 7, "er-feature-schema-v1", "mo:100:2026-09-18", 100, "TCS", direction, "ER_RS_CONTINUATION_V1",
                "ER_RS_CONTINUATION_V1/v1", 7, new BigDecimal("0.05"), 1, longSide ? "BULLISH" : "BEARISH", "IT",
                UUID.fromString("22222222-2222-2222-2222-222222222222"), T,
                "EMA_3_8_CROSS", trigger, 0.5, longSide ? "M5_SWING_LOW" : "M5_SWING_HIGH",
                longSide ? new BigDecimal("98") : new BigDecimal("102"), longSide ? "M5 swing low" : "M5 swing high");
    }

    private static RiskDecisionProposal proposal(
            RiskDecisionType decision,
            Direction direction,
            long quantity,
            BigDecimal entry,
            BigDecimal invalidation,
            BigDecimal stop,
            BigDecimal effectiveLoss) {
        BigDecimal approvedRisk = effectiveLoss.multiply(BigDecimal.valueOf(quantity));
        BigDecimal notional = entry.multiply(BigDecimal.valueOf(quantity));
        return new RiskDecisionProposal(
                "decision-key-1", "cand-1", "cand", T, TradingMode.ASSISTED_LIVE, "ER_RISK_V1", 1,
                PolicyState.VALIDATED, "ctx-1", 1, 42, "11111111-1111-1111-1111-111111111111", 7,
                1, 100, "TCS", direction, RiskState.NORMAL, decision,
                quantity, quantity, quantity,
                entry, invalidation, stop,
                effectiveLoss.subtract(new BigDecimal("0.15")), effectiveLoss, new BigDecimal("3.05"),
                approvedRisk.subtract(new BigDecimal("0.15")), approvedRisk, new BigDecimal("3.05"),
                notional, notional, new BigDecimal("10000"), new BigDecimal("1000000"),
                Map.of(), Map.of(), List.of(), List.of(), List.of(RiskReasonCode.OTHER), null,
                "decision=" + decision);
    }

    private static TradePlan plan(RiskDecisionType decision, long quantity, Direction direction) {
        BigDecimal entry = new BigDecimal("100");
        BigDecimal invalidation = direction.isLong() ? new BigDecimal("98") : new BigDecimal("102");
        BigDecimal stop = direction.isLong() ? new BigDecimal("97.95") : new BigDecimal("102.05");
        return TradePlanFactory.create(
                proposal(decision, direction, quantity, entry, invalidation, stop, new BigDecimal("2.20")),
                5L,
                lineage(direction, new BigDecimal("99.90")),
                policy(),
                T,
                CLOSE);
    }

    @Test
    void approveCreatesPlanWithinApprovedCeilings() {
        TradePlan plan = plan(RiskDecisionType.APPROVE, 2000, Direction.LONG);

        assertThat(plan.planKey()).isEqualTo(TradePlanFactory.planKeyFor("decision-key-1"));
        assertThat(plan.plannedQuantity()).isEqualTo(2000);
        assertThat(plan.approvedQuantityCeiling()).isEqualTo(2000);
        assertThat(plan.plannedRisk()).isEqualByComparingTo("4400");
        assertThat(plan.approvedRiskCeiling()).isEqualByComparingTo("4400");
        assertThat(plan.plannedNotional()).isEqualByComparingTo("200000");
        assertThat(plan.maximumPlannedLoss()).isEqualByComparingTo("4400");
        assertThat(plan.protectiveStop()).isEqualByComparingTo("97.95");
        assertThat(plan.structuralInvalidation()).isEqualByComparingTo("98");
        assertThat(plan.targetMethod()).isEqualTo("STRUCTURAL_UNRESOLVED");
        assertThat(plan.targetReference()).isNull();
        assertThat(plan.expectedRewardRisk()).isNull();
        assertThat(plan.validFrom()).isEqualTo(T);
        assertThat(plan.expiresAt()).isEqualTo(T.plusSeconds(1800));
        assertThat(plan.entryCutoffAt()).isEqualTo(CLOSE.minusSeconds(900));
        assertThat(plan.noChasePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void reduceCreatesPlanAtReducedQuantity() {
        TradePlan plan = plan(RiskDecisionType.REDUCE, 1200, Direction.LONG);
        assertThat(plan.plannedQuantity()).isEqualTo(1200);
        assertThat(plan.plannedRisk()).isEqualByComparingTo("2640");
    }

    @Test
    void otherDecisionsCannotCreatePlan() {
        for (RiskDecisionType decision : List.of(RiskDecisionType.REJECT, RiskDecisionType.HALT_REQUIRED)) {
            assertThatThrownBy(() -> TradePlanFactory.create(
                            proposal(decision, Direction.LONG, 100, new BigDecimal("100"), new BigDecimal("98"),
                                    new BigDecimal("97.95"), new BigDecimal("2.20")),
                            5L, lineage(Direction.LONG, null), policy(), T, CLOSE))
                    .isInstanceOf(TradePlanException.class)
                    .hasMessageContaining("APPROVE or REDUCE");
        }
    }

    @Test
    void wrongSideAndNonTickAlignedStopsAreRejected() {
        assertThatThrownBy(() -> TradePlanFactory.create(
                        proposal(RiskDecisionType.APPROVE, Direction.LONG, 100, new BigDecimal("100"),
                                new BigDecimal("101"), new BigDecimal("100.50"), new BigDecimal("1")),
                        5L, lineage(Direction.LONG, null), policy(), T, CLOSE))
                .isInstanceOf(TradePlanException.class)
                .hasMessageContaining("below entry");

        assertThatThrownBy(() -> TradePlanFactory.create(
                        proposal(RiskDecisionType.APPROVE, Direction.LONG, 100, new BigDecimal("100.001"),
                                new BigDecimal("98"), new BigDecimal("97.95"), new BigDecimal("2.20")),
                        5L, lineage(Direction.LONG, null), policy(), T, CLOSE))
                .isInstanceOf(TradePlanException.class)
                .hasMessageContaining("tick size");
    }

    @Test
    void shortPlanStopIsAboveEntry() {
        TradePlan plan = plan(RiskDecisionType.APPROVE, 500, Direction.SHORT);
        assertThat(plan.protectiveStop()).isEqualByComparingTo("102.05");
        assertThat(plan.direction()).isEqualTo(Direction.SHORT);
    }

    @Test
    void rMultipleTargetIsPlacedAtTheConfiguredRewardRisk() {
        TradePlanPolicy target = new TradePlanPolicy(
                "ER_TRADE_PLAN_V1_SYNTHETIC", 1, "trade-plan-policy/v1", 30, 15,
                new BigDecimal("2"), "REFERENCE_PRICE", "R_MULTIPLE", new BigDecimal("2"), "TICK_BUFFER");
        TradePlan plan = TradePlanFactory.create(
                proposal(RiskDecisionType.APPROVE, Direction.LONG, 2000, new BigDecimal("100"),
                        new BigDecimal("98"), new BigDecimal("97.95"), new BigDecimal("2.20")),
                5L, lineage(Direction.LONG, new BigDecimal("99.90")), target, T, CLOSE);
        // Stop distance 2.05; a 2R target from entry 100 is 100 + 2 * 2.05.
        assertThat(plan.targetReference()).isEqualByComparingTo("104.10");
        assertThat(plan.targetMethod()).isEqualTo("R_MULTIPLE");
    }

    @Test
    void eligibilityBoundariesAreExplicit() {
        TradePlan plan = plan(RiskDecisionType.APPROVE, 2000, Direction.LONG);

        PlanEligibility eligible = TradePlanEligibilityEvaluator.evaluate(plan, T.plusSeconds(60), true, "OPEN");
        assertThat(eligible.status()).isEqualTo(TradePlanStatus.ELIGIBLE);
        assertThat(eligible.permitsEntry()).isTrue();

        PlanEligibility atExpiry = TradePlanEligibilityEvaluator.evaluate(plan, plan.expiresAt(), true, "OPEN");
        assertThat(atExpiry.status()).isEqualTo(TradePlanStatus.EXPIRED);
        assertThat(atExpiry.reasons()).contains(TradePlanEligibilityEvaluator.TRIGGER_LIFETIME_EXPIRED);

        PlanEligibility atCutoff = TradePlanEligibilityEvaluator.evaluate(plan, plan.entryCutoffAt(), true, "OPEN");
        assertThat(atCutoff.status()).isEqualTo(TradePlanStatus.EXPIRED);

        PlanEligibility stale = TradePlanEligibilityEvaluator.evaluate(plan, T.plusSeconds(60), false, "OPEN");
        assertThat(stale.status()).isEqualTo(TradePlanStatus.PENDING);
        assertThat(stale.reasons()).contains(TradePlanEligibilityEvaluator.REQUIRED_INPUTS_STALE);

        PlanEligibility closed = TradePlanEligibilityEvaluator.evaluate(plan, T.plusSeconds(60), true, "CLOSED");
        assertThat(closed.status()).isEqualTo(TradePlanStatus.PENDING);

        TradePlan noValidity = TradePlanFactory.create(
                proposal(RiskDecisionType.APPROVE, Direction.LONG, 2000, new BigDecimal("100"), new BigDecimal("98"),
                        new BigDecimal("97.95"), new BigDecimal("2.20")),
                5L, lineage(Direction.LONG, null),
                new TradePlanPolicy("P", 1, "r", null, null, null, null, null, null, null), T, null);
        PlanEligibility unknown = TradePlanEligibilityEvaluator.evaluate(noValidity, T.plusSeconds(60), true, "OPEN");
        assertThat(unknown.status()).isEqualTo(TradePlanStatus.UNKNOWN);
    }
}
