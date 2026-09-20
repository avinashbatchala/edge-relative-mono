package com.edgerelative.application.risk.application;

import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.risk.application.port.RiskContextProvider;
import com.edgerelative.application.risk.domain.RiskCandidate;
import com.edgerelative.application.risk.domain.RiskContext;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.risk.domain.RiskEvaluator;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.risk.domain.RiskReasonCode;
import com.edgerelative.application.risk.persistence.RiskDecisionRepository;
import com.edgerelative.application.risk.persistence.RiskPolicyRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application orchestration for the risk engine (DD-03 §152): resolves authoritative inputs,
 * validates policy applicability, invokes the pure evaluator, and (for approvals) persists the
 * immutable decision and atomically reserves capacity.
 *
 * <p>{@link #preview} is explicitly non-authorizing and writes nothing. {@link #approve} re-resolves
 * the context under an account-session lock so concurrent approvals cannot overspend capacity.
 */
@Service
public class RiskApplicationService {

    private final RiskContextProvider contextProvider;
    private final RiskPolicyRepository policyRepository;
    private final RiskDecisionRepository repository;
    private final RiskEvaluator evaluator;
    private final NseTradingCalendar calendar;
    private final Clock clock;

    public RiskApplicationService(
            RiskContextProvider contextProvider,
            RiskPolicyRepository policyRepository,
            RiskDecisionRepository repository,
            RiskEvaluator evaluator,
            NseTradingCalendar calendar,
            Clock clock) {
        this.contextProvider = contextProvider;
        this.policyRepository = policyRepository;
        this.repository = repository;
        this.evaluator = evaluator;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Computes a proposal without reserving anything; never authorizing. */
    public RiskDecisionProposal preview(RiskCandidate candidate) {
        return evaluate(candidate);
    }

    /** Rechecks capacity under a lock, persists the decision, and reserves capacity if approved. */
    @Transactional
    public RiskDecisionProposal approve(RiskCandidate candidate) {
        Instant at = candidate.candidateAt() == null ? clock.instant() : candidate.candidateAt();
        LocalDate tradingDate = calendar.sessionDate(at);
        Optional<RiskPolicyRepository.ResolvedPolicy> resolved = policyRepository.resolve(candidate.policyCode());
        Optional<Authority> authority = authority(candidate);

        RiskContext context = contextProvider
                .current(candidate.brokerAccountId(), at)
                .orElseGet(() -> unavailableContext(candidate, at, tradingDate));

        if (authority.isPresent()) {
            repository.createAccountState(context, candidate.brokerAccountId());
            repository.lockAccountState(candidate.brokerAccountId(), tradingDate);
        }

        RiskDecisionProposal proposal = evaluator.evaluate(
                authority.map(Authority::candidate).orElse(candidate),
                context,
                resolved.map(RiskPolicyRepository.ResolvedPolicy::policy).orElse(null));

        if (authority.isEmpty() || resolved.isEmpty()) {
            return proposal; // no valid setup_observation / policy to anchor immutable evidence
        }

        Authority auth = authority.get();
        long contextId = repository.ensureContextSnapshot(
                context, auth.tenantId(), auth.brokerAccountId(), resolved.get().riskPolicyVersionId());
        long decisionId = repository.ensureDecision(
                proposal, auth.setupObservationId(), contextId, auth.tenantId(), auth.brokerAccountId(),
                auth.strategyVersionId(), resolved.get().riskPolicyVersionId());
        repository.insertReasons(decisionId, proposal);
        repository.ensureReservation(proposal, decisionId, tradingDate).ifPresent(reservationId -> repository
                .reserveCapacity(auth.brokerAccountId(), tradingDate, proposal.executionAdjustedRisk(),
                        proposal.approvedNotional()));
        return proposal;
    }

    private RiskDecisionProposal evaluate(RiskCandidate candidate) {
        Instant at = candidate.candidateAt() == null ? clock.instant() : candidate.candidateAt();
        LocalDate tradingDate = calendar.sessionDate(at);
        Optional<RiskPolicyRepository.ResolvedPolicy> resolved = policyRepository.resolve(candidate.policyCode());
        RiskContext context = contextProvider
                .current(candidate.brokerAccountId(), at)
                .orElseGet(() -> unavailableContext(candidate, at, tradingDate));
        RiskCandidate effective = authority(candidate).map(Authority::candidate).orElse(candidate);
        return evaluator.evaluate(effective, context, resolved.map(RiskPolicyRepository.ResolvedPolicy::policy).orElse(null));
    }

    /**
     * Re-derives setup eligibility from the authoritative observation. A client-supplied
     * {@code setup_valid=true} is never trusted; missing or mismatched lineage fails closed.
     */
    private Optional<Authority> authority(RiskCandidate candidate) {
        if (candidate.setupObservationId() <= 0) {
            return Optional.empty();
        }
        Record row = repository.findSetupObservation(candidate.setupObservationId());
        if (row == null) {
            return Optional.empty();
        }
        Long tenantId = row.get("tenant_id", Long.class);
        Long brokerAccountId = row.get("broker_account_id", Long.class);
        Long strategyVersionId = row.get("strategy_version_id", Long.class);
        Long instrumentId = row.get("instrument_id", Long.class);
        String status = row.get("setup_status", String.class);
        if (tenantId == null || brokerAccountId == null || strategyVersionId == null || instrumentId == null) {
            return Optional.empty();
        }
        boolean matches = brokerAccountId == candidate.brokerAccountId() && instrumentId == candidate.instrumentId();
        boolean valid = matches && "VALID".equals(status);
        RiskCandidate effective = new RiskCandidate(
                candidate.candidateKey(), candidate.candidateId(), tenantId, brokerAccountId, candidate.mode(),
                candidate.setupInstanceId(), candidate.setupObservationId(), candidate.strategyId(),
                candidate.strategyVersion(), Math.toIntExact(strategyVersionId), candidate.instrumentId(),
                candidate.symbol(), candidate.direction(), candidate.tickSize(), candidate.quantityIncrement(),
                candidate.proposedEntryPrice(), candidate.structuralInvalidation(), candidate.invalidationBasis(),
                candidate.candidateAt(), candidate.marketRegime(), candidate.marketRegimeKnown(),
                candidate.eventRiskKnown(), candidate.eventRiskBlocked(), candidate.sectorId(), candidate.sectorCode(),
                candidate.sectorEffectiveDate(), candidate.spreadBps(), candidate.expectedExecutableVolume(),
                candidate.medianDailyVolume(), candidate.brokerMaxQuantity(), candidate.requestedQuantity(), valid,
                status, candidate.featureSchemaVersion(), candidate.policyCode());
        return Optional.of(new Authority(effective, tenantId, brokerAccountId, strategyVersionId));
    }

    private RiskContext unavailableContext(RiskCandidate candidate, Instant at, LocalDate tradingDate) {
        UUID contextKey = UUID.nameUUIDFromBytes(
                ("risk-context:unavailable:" + candidate.brokerAccountId() + ":" + at).getBytes(StandardCharsets.UTF_8));
        RiskContext base = RiskContext.unavailable(contextKey.toString(), at);
        return RiskContext.builder(contextKey.toString(), 1, at, tradingDate)
                .losses(base.sessionRealizedLoss(), base.sessionDrawdown(), base.accountDrawdown(),
                        base.weeklyDrawdown(), base.monthlyDrawdown())
                .build();
    }

    private record Authority(RiskCandidate candidate, long tenantId, long brokerAccountId, long strategyVersionId) {

        long setupObservationId() {
            return candidate.setupObservationId();
        }
    }

    /** Expiry/cancellation hook for unconsumed pre-execution reservations. */
    @Transactional
    public boolean release(long brokerAccountId, String decisionKey, String reason) {
        Record decision = repository.findDecision(decisionKey);
        if (decision == null) {
            return false;
        }
        return repository.releaseReservation(brokerAccountId, decision.get("risk_decision_id", Long.class), reason);
    }

    public static RiskReasonCode[] vocabulary() {
        return RiskReasonCode.values();
    }
}
