package com.edgerelative.application.risk;

import static com.edgerelative.application.risk.RiskFixtures.candidate;
import static com.edgerelative.application.risk.RiskFixtures.dec;
import static com.edgerelative.application.risk.RiskFixtures.normalContext;
import static com.edgerelative.application.risk.RiskFixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.risk.domain.ConstraintEvaluation;
import com.edgerelative.application.risk.domain.LossProfile;
import com.edgerelative.application.risk.domain.QuantityCap;
import com.edgerelative.application.risk.domain.RiskContext;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.risk.domain.RiskDecisionType;
import com.edgerelative.application.risk.domain.RiskEvaluator;
import com.edgerelative.application.risk.domain.RiskMath;
import com.edgerelative.application.risk.domain.RiskReasonCode;
import com.edgerelative.application.risk.domain.RiskState;
import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskEvaluatorTest {

    private static final RiskEvaluator EVALUATOR = new RiskEvaluator();

    private static QuantityCap cap(RiskDecisionProposal proposal, String name) {
        return proposal.quantityCaps().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing cap " + name));
    }

    private static ConstraintEvaluation constraint(RiskDecisionProposal proposal, String name) {
        return proposal.constraints().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing constraint " + name));
    }

    @Test
    void longStopAndLossProfile() {
        LossProfile loss = RiskEvaluator.lossProfile(candidate().build(), policy());
        assertThat(loss.valid()).isTrue();
        assertThat(loss.protectiveStop()).isEqualByComparingTo("97.95");
        assertThat(loss.plannedLossPerUnit()).isEqualByComparingTo("2.05");
        assertThat(loss.effectiveLossPerUnit()).isEqualByComparingTo("2.20");
    }

    @Test
    void minimumStopFloorWidensProtestiveStopAndResizes() {
        // Entry 100, structural invalidation 98 (stop 97.95). With a 1-ATR floor and ATR 10, the
        // protective stop must move to 90, so planned loss becomes 10 per unit (quantity re-sizes).
        LossProfile loss = RiskEvaluator.lossProfile(
                candidate().referenceAtr(10.0).build(), policy().withMinStopAtr(dec("1.0")));
        assertThat(loss.valid()).isTrue();
        assertThat(loss.protectiveStop()).isEqualByComparingTo("90.00");
        assertThat(loss.plannedLossPerUnit()).isEqualByComparingTo("10.00");
    }

    @Test
    void shortStopAndLossProfile() {
        LossProfile loss = RiskEvaluator.lossProfile(
                candidate().direction(Direction.SHORT).invalidation("102").build(), policy());
        assertThat(loss.valid()).isTrue();
        assertThat(loss.protectiveStop()).isEqualByComparingTo("102.05");
        assertThat(loss.plannedLossPerUnit()).isEqualByComparingTo("2.05");
    }

    @Test
    void tickRoundingNeverMovesStopInsideProtectiveBoundary() {
        LossProfile longLoss = RiskEvaluator.lossProfile(
                candidate().invalidation("98.03").build(), policy());
        assertThat(longLoss.protectiveStop()).isEqualByComparingTo("97.95");
        assertThat(longLoss.protectiveStop()).isLessThanOrEqualTo(dec("97.98"));

        LossProfile shortLoss = RiskEvaluator.lossProfile(
                candidate().direction(Direction.SHORT).invalidation("101.97").build(), policy());
        assertThat(shortLoss.protectiveStop()).isEqualByComparingTo("102.05");
        assertThat(shortLoss.protectiveStop()).isGreaterThanOrEqualTo(dec("102.02"));
    }

    @Test
    void wrongSideStopIsRejectedWithoutAbs() {
        RiskDecisionProposal proposal = EVALUATOR.evaluate(
                candidate().invalidation("101").build(), normalContext(), policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.REJECT);
        assertThat(proposal.reasonCodes()).contains(RiskReasonCode.INVALID_STOP_DISTANCE);
        assertThat(proposal.approvedQuantity()).isZero();
    }

    @Test
    void approveSuppliesQuantityWhenNoneRequested() {
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), normalContext(), policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.APPROVE);
        assertThat(proposal.requestedQuantity()).isNull();
        assertThat(proposal.riskSizedQuantity()).isEqualTo(4545);
        assertThat(proposal.approvedQuantity()).isEqualTo(2000);
        assertThat(cap(proposal, "maxPositionNotional").binding()).isTrue();
        assertThat(cap(proposal, "liquidity").quantity()).isEqualTo(2000);
    }

    @Test
    void reduceWhenRequestedExceedsCapacity() {
        RiskDecisionProposal proposal = EVALUATOR.evaluate(
                candidate().requested(3000).build(), normalContext(), policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.REDUCE);
        assertThat(proposal.approvedQuantity()).isEqualTo(2000);
    }

    @Test
    void approveWhenRequestedFits() {
        RiskDecisionProposal proposal = EVALUATOR.evaluate(
                candidate().requested(1500).build(), normalContext(), policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.APPROVE);
        assertThat(proposal.approvedQuantity()).isEqualTo(1500);
    }

    @Test
    void tinyStopIsConstrainedByNotionalNotRisk() {
        RiskDecisionProposal proposal = EVALUATOR.evaluate(
                candidate().invalidation("99.9").build(), normalContext(), policy());
        assertThat(proposal.riskSizedQuantity()).isGreaterThan(2000);
        assertThat(proposal.approvedQuantity()).isEqualTo(2000);
        assertThat(proposal.reasonCodes()).isNotEmpty();
    }

    @Test
    void netExposureCrossingZeroUsesBothSigns() {
        RiskContext shortBook = RiskFixtures.contextBuilder()
                .exposures(dec("400000"), dec("-400000"))
                .build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), shortBook, policy());
        assertThat(cap(proposal, "netExposure").quantity()).isEqualTo(9000);
    }

    @Test
    void symbolConcentrationAccountsForExistingReservations() {
        RiskContext context = RiskFixtures.contextBuilder()
                .symbols(Map.of(100L, new RiskContext.SymbolExposure(100, 1L, "IT", 0,
                        dec("190000"), dec("0"), dec("0"))))
                .build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), context, policy());
        assertThat(cap(proposal, "symbolNotional").quantity()).isEqualTo(100);
        assertThat(cap(proposal, "symbolNotional").binding()).isTrue();
    }

    @Test
    void missingSectorMappingFailsClosed() {
        RiskDecisionProposal proposal = EVALUATOR.evaluate(
                candidate().sector(null, null).build(), normalContext(), policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.REJECT);
        assertThat(proposal.reasonCodes()).contains(RiskReasonCode.SECTOR_CONCENTRATION_LIMIT);
    }

    @Test
    void sessionLossesReduceRemainingCapacityAndProfitsDoNotIncreaseEquity() {
        RiskContext lossContext = RiskFixtures.contextBuilder()
                .losses(dec("28000"), dec("0"), dec("0"), dec("0"), dec("0"))
                .equity(dec("1000000"), dec("1200000"))
                .build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), lossContext, policy());
        assertThat(proposal.preDecisionCapacity().get("session.remaining")).isEqualByComparingTo("2000");
        assertThat(proposal.preDecisionCapacity().get("trade.ceiling")).isEqualByComparingTo("10000");
        assertThat(proposal.riskSizedQuantity()).isEqualTo(909);
    }

    @Test
    void reducedStateAppliesMultiplierOnce() {
        RiskContext context = RiskFixtures.contextBuilder().state(RiskState.REDUCED_1).build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), context, policy());
        assertThat(proposal.availableTradeRiskBudget()).isEqualByComparingTo("5000");
        assertThat(proposal.riskSizedQuantity()).isEqualTo(2272);
        assertThat(proposal.reasonCodes()).contains(RiskReasonCode.RISK_REDUCED_DRAWDOWN);
    }

    @Test
    void blockedAndHaltedStates() {
        RiskDecisionProposal blocked = EVALUATOR.evaluate(
                candidate().build(), RiskFixtures.contextBuilder().state(RiskState.NO_NEW_RISK).build(), policy());
        assertThat(blocked.decision()).isEqualTo(RiskDecisionType.REJECT);
        assertThat(blocked.reasonCodes()).contains(RiskReasonCode.RISK_STATE_BLOCKED);

        RiskDecisionProposal halted = EVALUATOR.evaluate(
                candidate().build(), RiskFixtures.contextBuilder().state(RiskState.HALTED).build(), policy());
        assertThat(halted.decision()).isEqualTo(RiskDecisionType.HALT_REQUIRED);
    }

    @Test
    void staleDataAndReconciliationFailClosed() {
        RiskDecisionProposal stale = EVALUATOR.evaluate(
                candidate().build(),
                RiskFixtures.contextBuilder().health("HEALTHY", "STALE", "MATCHED", true).build(), policy());
        assertThat(stale.reasonCodes()).contains(RiskReasonCode.DATA_QUALITY_BLOCKED);

        RiskDecisionProposal mismatch = EVALUATOR.evaluate(
                candidate().build(),
                RiskFixtures.contextBuilder().health("HEALTHY", "HEALTHY", "MISMATCH", true).build(), policy());
        assertThat(mismatch.reasonCodes()).contains(RiskReasonCode.RECONCILIATION_MISMATCH);

        RiskDecisionProposal unknownOrder = EVALUATOR.evaluate(
                candidate().build(),
                RiskFixtures.contextBuilder().health("HEALTHY", "HEALTHY", "MATCHED", false).build(), policy());
        assertThat(unknownOrder.reasonCodes()).contains(RiskReasonCode.ORDER_STATE_UNKNOWN);
    }

    @Test
    void stressRiskReducesPermission() {
        RiskContext context = RiskFixtures.contextBuilder()
                .risk(dec("0"), dec("99500"), dec("0"), dec("0"))
                .build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), context, policy());
        assertThat(cap(proposal, "stress").quantity()).isEqualTo(163);
        assertThat(proposal.approvedQuantity()).isEqualTo(163);
    }

    @Test
    void averagingDownAndPyramidingProhibited() {
        RiskContext context = RiskFixtures.contextBuilder()
                .symbols(Map.of(100L, new RiskContext.SymbolExposure(100, 1L, "IT", 50,
                        dec("5000"), dec("100"), dec("150"))))
                .build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().build(), context, policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.REJECT);
        assertThat(proposal.reasonCodes()).contains(RiskReasonCode.PROHIBITED_ADDITION);
    }

    @Test
    void zeroCapacityRejectsWithoutInventingQuantity() {
        RiskContext context = RiskFixtures.contextBuilder()
                .losses(dec("30000"), dec("0"), dec("0"), dec("0"), dec("0"))
                .build();
        RiskDecisionProposal proposal = EVALUATOR.evaluate(candidate().requested(500).build(), context, policy());
        assertThat(proposal.decision()).isEqualTo(RiskDecisionType.REJECT);
        assertThat(proposal.riskSizedQuantity()).isZero();
        assertThat(proposal.approvedQuantity()).isZero();
        assertThat(proposal.reasonCodes()).contains(RiskReasonCode.INSUFFICIENT_RISK_CAPACITY);
    }

    @Test
    void eventRiskAndInvalidSetupReject() {
        RiskDecisionProposal event = EVALUATOR.evaluate(
                candidate().eventBlocked(true).build(), normalContext(), policy());
        assertThat(event.reasonCodes()).contains(RiskReasonCode.EVENT_RISK_BLOCKED);

        RiskDecisionProposal invalid = EVALUATOR.evaluate(
                candidate().setupValid(false).setupStatus("INVALIDATED").build(), normalContext(), policy());
        assertThat(invalid.reasonCodes()).contains(RiskReasonCode.INVALID_SETUP);
    }

    @Test
    void deterministicReplayProducesIdenticalProposal() {
        RiskDecisionProposal first = EVALUATOR.evaluate(candidate().build(), normalContext(), policy());
        RiskDecisionProposal second = EVALUATOR.evaluate(candidate().build(), normalContext(), policy());
        assertThat(first).isEqualTo(second);
        assertThat(first.decisionKey()).isEqualTo(second.decisionKey());
    }

    @Test
    void exactBudgetBoundaryAndOneUnitAbove() {
        assertThat(RiskMath.floorQuantity(dec("100"), dec("2.20"))).isEqualTo(45);
        assertThat(RiskMath.floorQuantity(dec("99.999"), dec("2.20"))).isEqualTo(45);
        assertThat(RiskMath.floorQuantity(dec("98.999"), dec("2.20"))).isEqualTo(44);
        assertThat(RiskMath.floorQuantity(BigDecimal.ZERO, dec("2.20"))).isZero();
    }
}
