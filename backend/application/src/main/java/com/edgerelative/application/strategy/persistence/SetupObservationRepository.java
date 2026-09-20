package com.edgerelative.application.strategy.persistence;

import com.edgerelative.application.strategy.domain.ReasonCode;
import com.edgerelative.application.strategy.domain.SetupState;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import com.edgerelative.application.strategy.application.SetupObservationView;
import com.edgerelative.application.strategy.domain.StrategyIdentity;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only persistence for setup observations (DD-04B §(setup_observation)). A transition inserts
 * a new row; existing rows are never updated. Writes are idempotent on the deterministic observation
 * key, so replaying the same canonical input does not duplicate history.
 *
 * <p>Anchors are created only for real canonical M5 closes (DD-05 §128/§129) — never synthetic.
 */
@Repository
public class SetupObservationRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID DEFAULT_TENANT_KEY = deterministicKey("edge-relative:default-tenant");

    private final DSLContext dsl;

    public SetupObservationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Idempotently materializes the observation anchor for a real canonical M5 close. */
    public long ensureMarketObservation(
            long instrumentId, long timeframeId, Instant barCloseTime, String observationKey) {
        UUID key = observationKey == null || observationKey.isBlank()
                ? deterministicKey(
                        "market-observation:%d:%d:%s".formatted(instrumentId, timeframeId, barCloseTime))
                : UUID.fromString(observationKey);
        dsl.execute(
                "INSERT INTO market.market_observation (observation_key, instrument_id, timeframe_id, "
                        + "bar_close_timestamp, quality_status) VALUES (?, ?, ?, ?::timestamptz, 'GOOD') "
                        + "ON CONFLICT DO NOTHING",
                key,
                instrumentId,
                timeframeId,
                utc(barCloseTime));
        Record record = dsl.fetchOne(
                "SELECT market_observation_id FROM market.market_observation "
                        + "WHERE instrument_id = ? AND timeframe_id = ? AND bar_close_timestamp = ?::timestamptz",
                instrumentId,
                timeframeId,
                utc(barCloseTime));
        return record.get("market_observation_id", Long.class);
    }

    public Optional<Long> strategyVersionId() {
        Record record = dsl.fetchOne(
                "SELECT sv.strategy_version_id FROM control.strategy_version sv "
                        + "JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
                        + "WHERE s.code = ? ORDER BY sv.version DESC LIMIT 1",
                StrategyIdentity.STRATEGY_ID);
        return record == null ? Optional.empty() : Optional.of(record.get("strategy_version_id", Long.class));
    }

    /** @return true when a new observation row was written; false when it already existed. */
    public boolean append(
            StrategyEvaluationResult result,
            long strategyVersionId,
            long marketObservationId,
            Long sectorId) {
        String direction = result.direction() == null ? "NONE" : result.direction().name();
        String state = result.setupState().name();
        UUID observationKey = deterministicKey("setup-observation:%d:%d:%d:%s:%s:%s"
                .formatted(
                        strategyVersionId,
                        result.instrumentId(),
                        marketObservationId,
                        direction,
                        state,
                        result.setupInstanceId()));
        Record inserted = dsl.fetchOne(
                "INSERT INTO operational.setup_observation (setup_observation_key, tenant_id, market_observation_id, "
                        + "strategy_version_id, instrument_id, observed_at, direction, setup_status, entry_pattern, "
                        + "structural_invalidation, structural_rr, market_regime, sector_id, correlation_id, "
                        + "setup_instance_id, initialization_reason, explanation) "
                        + "VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) "
                        + "ON CONFLICT (strategy_version_id, instrument_id, market_observation_id, direction, "
                        + "setup_status, COALESCE(setup_instance_id, '00000000-0000-0000-0000-000000000000'::uuid)) "
                        + "DO NOTHING RETURNING setup_observation_id",
                observationKey,
                ensureTenant(),
                marketObservationId,
                strategyVersionId,
                result.instrumentId(),
                utc(result.evaluationTimestamp()),
                direction,
                state,
                result.setupFamily() == null ? null : result.setupFamily().name(),
                result.invalidation() == null ? null : result.invalidation().invalidationLevel(),
                result.structuralRR(),
                result.marketRegime(),
                sectorId,
                result.setupInstanceId(),
                result.setupInstanceId(),
                result.initialization() == null ? null : result.initialization().name(),
                JSON.writeValueAsString(explanation(result)));
        return inserted != null;
    }

    public List<SetupObservationView> latestForInstrument(long instrumentId) {
        return dsl.fetch(
                        "SELECT so.observed_at, so.direction, so.setup_status, so.entry_pattern, "
                                + "so.structural_invalidation, so.structural_rr, so.explanation, "
                                + "sv.version AS strategy_version "
                                + "FROM operational.setup_observation so "
                                + "JOIN control.strategy_version sv ON sv.strategy_version_id = so.strategy_version_id "
                                + "WHERE so.instrument_id = ? ORDER BY so.observed_at DESC LIMIT 20",
                        instrumentId)
                .map(record -> new SetupObservationView(
                        record.get("observed_at", OffsetDateTime.class).toInstant(),
                        record.get("direction", String.class),
                        record.get("setup_status", String.class),
                        record.get("entry_pattern", String.class),
                        record.get("structural_invalidation", BigDecimal.class),
                        record.get("structural_rr", Double.class),
                        record.get("strategy_version", Integer.class),
                        record.get("explanation", String.class)));
    }

    private Map<String, Object> explanation(StrategyEvaluationResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("setupInstanceId", result.setupInstanceId() == null ? null : result.setupInstanceId().toString());
        payload.put("initialization", result.initialization() == null ? null : result.initialization().name());
        payload.put("strategyVersion", result.strategyVersion());
        payload.put("parameterSetId", result.parameterSetId());
        payload.put("parameterVersion", result.parameterVersion());
        payload.put("primaryReasonCode", result.primaryReasonCode() == null ? null : result.primaryReasonCode().name());
        payload.put(
                "reasonCodes",
                result.reasonCodes().stream().map(ReasonCode::name).toList());
        payload.put(
                "hardGates",
                result.hardGates().stream()
                        .map(gate -> Map.of(
                                "gate", gate.gateCode().name(),
                                "status", gate.status().name(),
                                "reason", gate.reasonCode() == null ? "" : gate.reasonCode().name()))
                        .toList());
        payload.put(
                "qualityFactors",
                result.qualityFactors().stream()
                        .map(factor -> Map.of(
                                "factor", factor.factor().name(),
                                "present", factor.present(),
                                "available", factor.available()))
                        .toList());
        if (result.trigger() != null) {
            payload.put("triggerType", result.trigger().triggerType());
            payload.put("triggerLevel", result.trigger().triggerLevel());
            payload.put("entryExtensionAtr", result.trigger().entryExtensionAtr());
        }
        if (result.invalidation() != null) {
            payload.put("invalidationType", result.invalidation().invalidationType());
            payload.put("invalidationLevel", result.invalidation().invalidationLevel());
        }
        payload.put("valid", result.valid());
        return payload;
    }

    private long ensureTenant() {
        dsl.execute(
                "INSERT INTO operational.tenant (tenant_key, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
                DEFAULT_TENANT_KEY,
                "Default Operator");
        return dsl.fetchOne(
                        "SELECT tenant_id FROM operational.tenant WHERE tenant_key = ?", DEFAULT_TENANT_KEY)
                .get("tenant_id", Long.class);
    }

    private static UUID deterministicKey(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toString();
    }
}
