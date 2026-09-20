package com.edgerelative.application.feature.persistence;

import com.edgerelative.application.feature.domain.FeatureDefinition;
import com.edgerelative.application.feature.domain.FeatureDefinitionRegistry;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * jOOQ persistence for feature snapshots. Inserts are idempotent and append-only; the database
 * triggers reject any update or delete so a historical feature version can never be rewritten
 * (DD-05 §39). Parameter hashes are stored with every value so lineage can distinguish two parameter
 * sets that share a semantic version.
 */
@Repository
public class JdbcFeatureSnapshotRepository implements FeatureSnapshotStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DSLContext dsl;

    public JdbcFeatureSnapshotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean save(FeatureSnapshot snapshot, long timeframeId, String sourceRevision) {
        snapshot.features().keySet().forEach(this::ensureDefinition);
        UUID key = deterministicKey(snapshot, timeframeId);
        Record inserted = dsl.fetchOne(
                "INSERT INTO market.feature_snapshot (snapshot_key, instrument_id, timeframe_id, anchor_timestamp, "
                        + "feature_schema_version, calculation_version, snapshot_quality, snapshot_availability, "
                        + "market_instrument_id, sector_id, sector_instrument_id, source_data_revision) "
                        + "VALUES (?, ?, ?, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (snapshot_key) DO NOTHING RETURNING feature_snapshot_id",
                key,
                snapshot.instrumentId(),
                timeframeId,
                utc(snapshot.anchorTimestamp()),
                snapshot.featureSchemaVersion(),
                calculationVersion(snapshot),
                snapshot.quality().name(),
                snapshot.availability().name(),
                snapshot.benchmark() == null ? null : snapshot.benchmark().marketInstrumentId(),
                snapshot.benchmark() == null ? null : snapshot.benchmark().sectorId(),
                snapshot.benchmark() == null ? null : snapshot.benchmark().sectorInstrumentId(),
                sourceRevision);
        if (inserted == null) {
            return false;
        }
        long snapshotId = inserted.get("feature_snapshot_id", Long.class);
        List<Query> inserts = new ArrayList<>();
        int ordinal = 1;
        for (FeatureValue value : snapshot.features().values()) {
            inserts.add(dsl.query(
                    "INSERT INTO market.feature_snapshot_value (feature_snapshot_id, feature_code, feature_version, "
                            + "parameter_hash, ordinal, value, label, quality, availability, lineage) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING",
                    snapshotId,
                    value.featureKey(),
                    value.version().displayVersion(),
                    value.version().parameterHash(),
                    ordinal++,
                    value.value(),
                    value.label(),
                    value.quality().name(),
                    value.availability().name(),
                    JSON.writeValueAsString(value.lineage())));
        }
        if (!inserts.isEmpty()) {
            dsl.batch(inserts).execute();
        }
        return true;
    }

    private static String calculationVersion(FeatureSnapshot snapshot) {
        return com.edgerelative.application.feature.policy.CalculationVersions.CURRENT;
    }

    private void ensureDefinition(String featureKey) {
        FeatureDefinition definition = FeatureDefinitionRegistry.require(featureKey);
        UUID key = UUID.nameUUIDFromBytes(
                ("feature:" + definition.featureKey()).getBytes(StandardCharsets.UTF_8));
        dsl.execute(
                "INSERT INTO control.feature_definition (feature_key, code, name, description, value_type) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (code) DO NOTHING",
                key,
                definition.featureKey(),
                definition.name(),
                definition.description(),
                definition.valueType());
    }

    private static UUID deterministicKey(FeatureSnapshot snapshot, long timeframeId) {
        String semantic = snapshot.instrumentId()
                + "|" + timeframeId
                + "|" + snapshot.anchorTimestamp()
                + "|" + snapshot.featureSchemaVersion()
                + "|" + com.edgerelative.application.feature.policy.CalculationVersions.CURRENT;
        return UUID.nameUUIDFromBytes(semantic.getBytes(StandardCharsets.UTF_8));
    }

    private static String utc(java.time.Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC).toString();
    }
}
