package com.edgerelative.application.tradeplan.application;

import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.risk.domain.RiskDecisionProposal;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.tradeplan.domain.PlanEligibility;
import com.edgerelative.application.tradeplan.domain.PlanLineage;
import com.edgerelative.application.tradeplan.domain.TradePlan;
import com.edgerelative.application.tradeplan.domain.TradePlanEligibilityEvaluator;
import com.edgerelative.application.tradeplan.domain.TradePlanFactory;
import com.edgerelative.application.tradeplan.persistence.TradePlanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Record;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Creates and reads immutable trade plans. Creation runs inside the risk-approval transaction (the
 * caller joins this transaction), so approval, reservation, and plan creation commit or roll back
 * together. Reads never create or reapprove anything.
 */
@Service
@EnableConfigurationProperties(TradePlanProperties.class)
public class TradePlanService {

    public static final String SESSION_OPEN = "OPEN";

    private final TradePlanRepository repository;
    private final TradePlanProperties properties;
    private final NseTradingCalendar calendar;
    private final JsonMapper json;
    private final Clock clock;

    public TradePlanService(
            TradePlanRepository repository,
            TradePlanProperties properties,
            NseTradingCalendar calendar,
            JsonMapper json,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.calendar = calendar;
        this.json = json;
        this.clock = clock;
    }

    /** Idempotent: repeated calls for the same approved decision return the same persisted plan. */
    @Transactional
    public Optional<TradePlan> createFromApproval(RiskDecisionProposal proposal, long riskDecisionId, long tenantId) {
        if (proposal == null || !proposal.decision().authorizesNewRisk()) {
            return Optional.empty();
        }
        Optional<Record> lineageRow = repository.findSetupLineage(proposal.setupObservationId());
        if (lineageRow.isEmpty()) {
            return Optional.empty();
        }
        PlanLineage lineage = lineage(lineageRow.get(), proposal, tenantId);
        Instant createdAt = clock.instant();
        Instant sessionClose = calendar.sessionClose(calendar.sessionDate(createdAt));
        TradePlan plan = TradePlanFactory.create(
                proposal, riskDecisionId, lineage, properties.toPolicy(), createdAt, sessionClose);
        long planId = repository.ensurePlan(plan);
        repository.recordEvent(planId, plan.planKey(), "CREATED", createdAt, plan.explanation());
        return Optional.of(plan);
    }

    public Optional<TradePlanRow> byKey(String planKey) {
        return repository.findByKey(planKey);
    }

    public Optional<TradePlanRow> byDecision(String decisionKey) {
        return repository.findByDecision(decisionKey);
    }

    public List<TradePlanRow> bySetup(long setupObservationId) {
        return repository.findBySetup(setupObservationId);
    }

    public Optional<TradePlanRow> latestForInstrument(long instrumentId) {
        return repository.findLatestForInstrument(instrumentId);
    }

    /**
     * Server-side eligibility. {@code requiredInputsFresh} must come from the caller's authoritative
     * freshness check; this method never refreshes or reapproves.
     */
    public PlanEligibility eligibility(TradePlanRow row, boolean requiredInputsFresh) {
        Instant now = clock.instant();
        return TradePlanEligibilityEvaluator.evaluate(
                row.validFrom(), row.expiresAt(), row.entryCutoffAt(), now, requiredInputsFresh,
                sessionContext(now));
    }

    public String sessionContext(Instant now) {
        LocalDate sessionDate = calendar.sessionDate(now);
        if (!calendar.isTradingDay(sessionDate)) {
            return "NON_TRADING_DAY";
        }
        if (now.isBefore(calendar.sessionOpen(sessionDate))) {
            return "PRE_OPEN";
        }
        if (!now.isBefore(calendar.sessionClose(sessionDate))) {
            return "CLOSED";
        }
        return SESSION_OPEN;
    }

    private PlanLineage lineage(Record row, RiskDecisionProposal proposal, long tenantId) {
        String explanation = row.get("explanation", String.class);
        JsonNode node = explanation == null ? null : json.readTree(explanation);
        UUID setupInstance = row.get("setup_instance_id", UUID.class);
        return new PlanLineage(
                row.get("setup_observation_id", Long.class),
                setupInstance == null ? null : setupInstance.toString(),
                row.get("entry_pattern", String.class),
                "VALID",
                tenantId,
                proposal.brokerAccountId(),
                row.get("market_observation_id", Long.class),
                null,
                "mo:" + row.get("market_observation_id", Long.class),
                proposal.instrumentId(),
                row.get("canonical_symbol", String.class),
                Direction.valueOf(row.get("direction", String.class)),
                row.get("strategy_id", String.class),
                "v" + row.get("strategy_version_number", Integer.class),
                row.get("strategy_version_id", Long.class) == null
                        ? 0
                        : row.get("strategy_version_id", Long.class).intValue(),
                row.get("tick_size", BigDecimal.class),
                row.get("lot_size", Long.class) == null ? 1L : row.get("lot_size", Long.class),
                null,
                row.get("sector_code", String.class),
                null,
                instant(row, "observed_at"),
                text(node, "triggerType"),
                decimal(node, "triggerLevel"),
                dbl(node, "entryExtensionAtr"),
                text(node, "invalidationType"),
                decimal(node, "invalidationLevel"),
                null);
    }

    private static Instant instant(Record row, String column) {
        OffsetDateTime value = row.get(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.get(field) == null || node.get(field).isNull()
                ? null
                : node.get(field).asString();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return node == null || node.get(field) == null || node.get(field).isNull()
                ? null
                : node.get(field).decimalValue();
    }

    private static Double dbl(JsonNode node, String field) {
        return node == null || node.get(field) == null || node.get(field).isNull()
                ? null
                : node.get(field).asDouble();
    }
}
