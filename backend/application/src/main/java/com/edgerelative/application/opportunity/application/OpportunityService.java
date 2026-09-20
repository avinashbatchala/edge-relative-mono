package com.edgerelative.application.opportunity.application;

import com.edgerelative.application.opportunity.api.OpportunityRow;
import com.edgerelative.application.risk.domain.RiskDecisionType;
import com.edgerelative.application.strategy.application.StrategyParametersProvider;
import com.edgerelative.application.tradeplan.application.TradePlanRow;
import com.edgerelative.application.tradeplan.application.TradePlanService;
import com.edgerelative.application.tradeplan.application.TradePlanTrustPort;
import com.edgerelative.application.watchlist.WatchlistService;
import com.edgerelative.application.watchlist.api.WatchlistEntry;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

/**
 * Read-only composition of the latest setup observation, the latest risk decision, and any approved
 * plan per watchlist instrument. It never evaluates risk or creates a plan: absent evaluation is
 * reported as awaiting/unavailable rather than fabricated.
 */
@Service
public class OpportunityService {

    public static final String RISK_UNAVAILABLE = "RISK_UNAVAILABLE";
    public static final String AWAITING_RISK = "AWAITING_RISK_EVALUATION";
    public static final String NO_SETUP = "NO_SETUP";

    private final WatchlistService watchlist;
    private final DSLContext dsl;
    private final StrategyParametersProvider riskParameters;
    private final TradePlanService tradePlans;
    private final TradePlanTrustPort trustPort;

    public OpportunityService(
            WatchlistService watchlist,
            DSLContext dsl,
            StrategyParametersProvider riskParameters,
            TradePlanService tradePlans,
            TradePlanTrustPort trustPort) {
        this.watchlist = watchlist;
        this.dsl = dsl;
        this.riskParameters = riskParameters;
        this.tradePlans = tradePlans;
        this.trustPort = trustPort;
    }

    public java.util.List<OpportunityRow> list() {
        boolean riskEnabled = riskParameters.enabled();
        return watchlist.list().entries().stream().map(entry -> row(entry, riskEnabled)).toList();
    }

    private OpportunityRow row(WatchlistEntry entry, boolean riskEnabled) {
        Record setup = dsl.fetchOne(
                "SELECT setup_observation_id, setup_status, direction, entry_pattern, observed_at, "
                        + "setup_instance_id FROM operational.setup_observation WHERE instrument_id = ? "
                        + "ORDER BY observed_at DESC LIMIT 1",
                entry.instrumentId());
        if (setup == null) {
            return base(entry, null, NO_SETUP, null, null, null, null, NO_SETUP, null, null, null, null,
                    false, null);
        }
        long setupId = setup.get("setup_observation_id", Long.class);
        String setupStatus = setup.get("setup_status", String.class);
        String direction = setup.get("direction", String.class);
        String family = setup.get("entry_pattern", String.class);
        java.time.Instant observedAt = instant(setup.get("observed_at", OffsetDateTime.class));
        java.util.UUID instance = setup.get("setup_instance_id", java.util.UUID.class);

        Record decision = dsl.fetchOne(
                "SELECT risk_decision_id, decision_key, decision FROM operational.risk_decision "
                        + "WHERE setup_observation_id = ? ORDER BY decision_at DESC LIMIT 1",
                setupId);
        String riskState;
        String decisionKey = null;
        String decisionType = null;
        String rejection = null;
        String planKey = null;
        String planStatus = null;
        boolean planPermits = false;
        java.time.Instant planExpires = null;

        if (!riskEnabled) {
            riskState = RISK_UNAVAILABLE;
        } else if (decision == null) {
            riskState = AWAITING_RISK;
        } else {
            decisionKey = decision.get("decision_key", java.util.UUID.class).toString();
            decisionType = decision.get("decision", String.class);
            riskState = decisionType;
            if (RiskDecisionType.REJECT.name().equals(decisionType)
                    || RiskDecisionType.HALT_REQUIRED.name().equals(decisionType)) {
                rejection = firstReason(decision.get("risk_decision_id", Long.class));
            } else {
                java.util.Optional<TradePlanRow> plan = tradePlans.byDecision(decisionKey);
                if (plan.isPresent()) {
                    TradePlanRow planRow = plan.get();
                    planKey = planRow.planKey();
                    var eligibility = tradePlans.eligibility(planRow, trustPort.requiredInputsFresh(planRow));
                    planStatus = eligibility.status().name();
                    planPermits = eligibility.permitsEntry();
                    planExpires = planRow.expiresAt();
                }
            }
        }

        return base(entry, setupId, setupStatus, direction, family, observedAt,
                instance == null ? null : instance.toString(), riskState, decisionKey, rejection,
                planKey, planStatus, planPermits, planExpires);
    }

    private String firstReason(long riskDecisionId) {
        Record reason = dsl.fetchOne(
                "SELECT reason_code FROM operational.risk_decision_reason WHERE risk_decision_id = ? "
                        + "ORDER BY ordinal LIMIT 1",
                riskDecisionId);
        return reason == null ? null : reason.get("reason_code", String.class);
    }

    private static OpportunityRow base(
            WatchlistEntry entry,
            Long setupObservationId,
            String setupStatus,
            String direction,
            String family,
            java.time.Instant observedAt,
            String setupInstanceId,
            String riskState,
            String riskDecisionKey,
            String rejectionReason,
            String planKey,
            String planEligibilityStatus,
            boolean planPermitsEntry,
            java.time.Instant planExpiresAt) {
        return new OpportunityRow(
                entry.instrumentId(), entry.symbol(), entry.name(), entry.exchange(), entry.segment(),
                "M5", setupObservationId, setupStatus, direction, family, observedAt, setupInstanceId,
                riskState, riskDecisionKey, riskState, rejectionReason, planKey, planEligibilityStatus,
                planPermitsEntry, planExpiresAt);
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
