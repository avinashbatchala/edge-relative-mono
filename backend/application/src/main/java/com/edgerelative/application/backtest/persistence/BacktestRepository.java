package com.edgerelative.application.backtest.persistence;

import com.edgerelative.application.backtest.application.BacktestRunRow;
import com.edgerelative.application.backtest.domain.BacktestRejection;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.domain.EquityPoint;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Persistence for backtest runs, immutable specs, and the append-only simulated ledger. */
@Repository
public class BacktestRepository {

    private final DSLContext dsl;
    private final JsonMapper json;

    public BacktestRepository(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    public record RunIds(long experimentRunId, long backtestRunId) {
    }

    public long ensureDataset(String code, String checksum) {
        long datasetId = dsl.fetchOne(
                        "INSERT INTO research.dataset (dataset_key, code, name, dataset_type, description) "
                                + "VALUES (?, ?, ?, 'BACKTEST', 'Canonical PostgreSQL candle dataset') "
                                + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING dataset_id",
                        UUID.nameUUIDFromBytes(("dataset:" + code).getBytes(StandardCharsets.UTF_8)), code, code)
                .get("dataset_id", Long.class);
        dsl.execute(
                "INSERT INTO research.dataset_version (dataset_version_key, dataset_id, version, status, "
                        + "storage_uri, checksum, code_version, committed_at) "
                        + "VALUES (?, ?, 1, 'COMMITTED', 'postgres://canonical', ?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (dataset_id, version) DO NOTHING",
                UUID.nameUUIDFromBytes(("dataset-version:" + code).getBytes(StandardCharsets.UTF_8)), datasetId,
                checksum, "er-backtest-engine-v1");
        return dsl.fetchOne(
                        "SELECT dataset_version_id FROM research.dataset_version WHERE dataset_id = ? AND version = 1",
                        datasetId)
                .get("dataset_version_id", Long.class);
    }

    public long strategyVersionId() {
        Record record = dsl.fetchOne(
                "SELECT sv.strategy_version_id FROM control.strategy_version sv "
                        + "JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
                        + "WHERE s.code = 'ER_RS_CONTINUATION_V1' ORDER BY sv.version DESC LIMIT 1");
        return record == null ? 0L : record.get("strategy_version_id", Long.class);
    }

    public RunIds createRun(
            String runKey,
            String specJson,
            String fullSpecJson,
            String costJson,
            LocalDate start,
            LocalDate end,
            long universeSize,
            BigDecimal startingCapital,
            String currency,
            long seed,
            String requestedBy,
            long datasetVersionId,
            Long selectedStrategyVersionId,
            Long selectedRiskPolicyVersionId) {
        Long strategyVersionId = selectedStrategyVersionId != null ? selectedStrategyVersionId
                : (strategyVersionId() == 0L ? null : strategyVersionId());
        long experimentId = dsl.fetchOne(
                        "INSERT INTO research.experiment (experiment_key, code, hypothesis, created_by) "
                                + "VALUES (?, 'BACKTEST_ER_RS_V1', 'Chronological replay of ER_RS_CONTINUATION_V1', ?) "
                                + "ON CONFLICT (code) DO UPDATE SET hypothesis = EXCLUDED.hypothesis RETURNING experiment_id",
                        UUID.nameUUIDFromBytes("experiment:BACKTEST_ER_RS_V1".getBytes(StandardCharsets.UTF_8)),
                        requestedBy)
                .get("experiment_id", Long.class);
        Record run = dsl.fetchOne(
                        "INSERT INTO research.experiment_run (run_key, experiment_id, status, strategy_version_id, "
                                + "dataset_version_id, date_range_start, date_range_end, parameters, cost_model, code_version, "
                                + "requested_by) "
                                + "VALUES (?, ?, 'CREATED', ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?) "
                                + "ON CONFLICT (run_key) DO NOTHING "
                                + "RETURNING experiment_run_id",
                        UUID.fromString(runKey), experimentId, strategyVersionId,
                        datasetVersionId, start, end, specJson, costJson, "er-backtest-engine-v1", requestedBy);
        if (run == null) {
            Record existing = dsl.fetchOne(
                    "SELECT er.experiment_run_id, br.backtest_run_id FROM research.experiment_run er "
                            + "JOIN research.backtest_run br ON br.experiment_run_id = er.experiment_run_id "
                            + "WHERE er.run_key = ?",
                    UUID.fromString(runKey));
            return new RunIds(existing.get("experiment_run_id", Long.class), existing.get("backtest_run_id", Long.class));
        }
        long experimentRunId = run.get("experiment_run_id", Long.class);
        long backtestRunId = dsl.fetchOne(
                        "INSERT INTO research.backtest_run (run_key, experiment_run_id, strategy_version_id, "
                                + "risk_policy_version_id, dataset_version_id, status, seed, universe_size, starting_capital, "
                                + "currency, engine_revision) "
                                + "VALUES (?, ?, ?, ?, ?, 'CREATED', ?, ?, ?, ?, 'er-backtest-engine-v1') RETURNING backtest_run_id",
                        UUID.fromString(runKey), experimentRunId, strategyVersionId, selectedRiskPolicyVersionId,
                        datasetVersionId, seed, (int) universeSize, startingCapital, currency)
                .get("backtest_run_id", Long.class);
        dsl.execute(
                "INSERT INTO research.backtest_run_spec (spec_key, experiment_run_id, spec, dataset_manifest, checksum, code_version) "
                        + "VALUES (?, ?, ?::jsonb, ?::jsonb, ?, 'er-backtest-engine-v1') ON CONFLICT (experiment_run_id) DO NOTHING",
                UUID.nameUUIDFromBytes(("spec:" + runKey).getBytes(StandardCharsets.UTF_8)), experimentRunId, fullSpecJson,
                json.writeValueAsString(Map.of("datasetVersionId", datasetVersionId)), "er-backtest-engine-v1");
        return new RunIds(experimentRunId, backtestRunId);
    }

    /** The full resolved spec persisted at start, used to replay a single-instrument timeline. */
    public java.util.Optional<String> findSpec(String runKey) {
        Record record = dsl.fetchOne(
                "SELECT brs.spec::text AS spec FROM research.backtest_run_spec brs "
                        + "JOIN research.backtest_run br ON br.experiment_run_id = brs.experiment_run_id "
                        + "WHERE br.run_key = ? LIMIT 1",
                UUID.fromString(runKey));
        return record == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(record.get("spec", String.class));
    }

    public void markRunning(long experimentRunId, long backtestRunId) {
        dsl.execute("UPDATE research.experiment_run SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP "
                + "WHERE experiment_run_id = ? AND status = 'CREATED'", experimentRunId);
        dsl.execute("UPDATE research.backtest_run SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP "
                + "WHERE backtest_run_id = ? AND status = 'CREATED'", backtestRunId);
    }

    public void updateProgress(long experimentRunId, long events, Instant through, long total) {
        dsl.execute("UPDATE research.experiment_run SET progress_events = ?, progress_through = ?::timestamptz, "
                + "progress_total = ? WHERE experiment_run_id = ?", events, utc(through), total, experimentRunId);
    }

    public void complete(long experimentRunId, long backtestRunId, Map<String, Object> metrics) {
        dsl.execute("UPDATE research.experiment_run SET status = 'SUCCEEDED', completed_at = CURRENT_TIMESTAMP, "
                + "result_summary = ?::jsonb WHERE experiment_run_id = ?", json.writeValueAsString(metrics), experimentRunId);
        dsl.execute("UPDATE research.backtest_run SET status = 'SUCCEEDED', completed_at = CURRENT_TIMESTAMP, "
                + "metrics = ?::jsonb WHERE backtest_run_id = ?", json.writeValueAsString(metrics), backtestRunId);
    }

    public void fail(long experimentRunId, long backtestRunId, String message) {
        dsl.execute("UPDATE research.experiment_run SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, "
                + "failure = ?::jsonb WHERE experiment_run_id = ?",
                json.writeValueAsString(Map.of("message", message == null ? "unknown" : message)), experimentRunId);
        dsl.execute("UPDATE research.backtest_run SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP "
                + "WHERE backtest_run_id = ?", backtestRunId);
    }

    public boolean cancel(String runKey) {
        int updated = dsl.execute(
                "UPDATE research.experiment_run SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP "
                        + "WHERE run_key = ? AND status IN ('CREATED','RUNNING')", UUID.fromString(runKey));
        dsl.execute("UPDATE research.backtest_run SET status = 'CANCELLED', completed_at = CURRENT_TIMESTAMP "
                + "WHERE run_key = ? AND status IN ('CREATED','RUNNING')", UUID.fromString(runKey));
        return updated > 0;
    }

    public java.util.Optional<String> runStatus(String runKey) {
        Record record = dsl.fetchOne("SELECT status FROM research.experiment_run WHERE run_key = ?", UUID.fromString(runKey));
        return record == null ? java.util.Optional.empty() : java.util.Optional.of(record.get("status", String.class));
    }

    public boolean isCancelled(String runKey) {
        Record record = dsl.fetchOne("SELECT status FROM research.experiment_run WHERE run_key = ?", UUID.fromString(runKey));
        return record != null && "CANCELLED".equals(record.get("status", String.class));
    }

    public void insertTrades(long backtestRunId, List<BacktestTrade> trades) {
        for (BacktestTrade trade : trades) {
            dsl.execute(
                    "INSERT INTO research.backtest_trade (trade_key, backtest_run_id, instrument_id, symbol, direction, "
                            + "entry_pattern, entry_at, entry_price, initial_risk_per_unit, exit_at, exit_price, quantity, "
                            + "gross_pnl, explicit_costs, net_pnl, realized_r, holding_seconds, exit_reason, ambiguous_bars, "
                            + "cost_breakdown, plan_key, decision_key, mfe_r, mae_r) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::timestamptz, ?, ?, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) "
                            + "ON CONFLICT (trade_key) DO NOTHING",
                    UUID.fromString(trade.tradeKey()), backtestRunId, trade.instrumentId(), trade.symbol(),
                    trade.direction().name(), trade.entryPattern(), utc(trade.entryAt()), trade.entryPrice(),
                    trade.initialRiskPerUnit(), utc(trade.exitAt()), trade.exitPrice(), trade.quantity(), trade.grossPnl(),
                    trade.explicitCosts(), trade.netPnl(), trade.realizedR(), trade.holdingSeconds(), trade.exitReason(),
                    trade.ambiguousBars(), json.writeValueAsString(trade.costBreakdown()), trade.planKey(),
                    trade.decisionKey(), trade.mfeR(), trade.maeR());
        }
    }

    public void insertEquity(long backtestRunId, List<EquityPoint> points) {
        for (EquityPoint point : points) {
            dsl.execute(
                    "INSERT INTO research.backtest_equity_point (backtest_run_id, at, equity, cash, gross_exposure, "
                            + "net_exposure, high_water, drawdown, drawdown_pct, open_positions) "
                            + "VALUES (?, ?::timestamptz, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (backtest_run_id, at) DO NOTHING",
                    backtestRunId, utc(point.at()), point.equity(), point.cash(), point.grossExposure(),
                    point.netExposure(), point.highWater(), point.drawdown(), point.drawdownPct(), point.openPositions());
        }
    }

    public void insertRejections(long backtestRunId, List<BacktestRejection> rejections) {
        for (BacktestRejection rejection : rejections) {
            dsl.execute(
                    "INSERT INTO research.backtest_rejection (backtest_run_id, at, instrument_id, direction, reason_code, detail) "
                            + "VALUES (?, ?::timestamptz, ?, ?, ?, ?::jsonb)",
                    backtestRunId, utc(rejection.at()), rejection.instrumentId(), rejection.direction().name(),
                    rejection.reasonCode(), json.writeValueAsString(Map.of("detail", rejection.detail() == null ? "" : rejection.detail())));
        }
    }

    public List<Long> findInstrumentIds(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (String symbol : symbols) {
            Record record = dsl.fetchOne(
                    "SELECT instrument_id FROM reference.instrument WHERE canonical_symbol = ? LIMIT 1", symbol);
            if (record != null) {
                ids.add(record.get("instrument_id", Long.class));
            }
        }
        return ids;
    }

    public List<BacktestTrade> findTrades(String runKey, String symbol, int limit, int offset) {
        // A null symbol binds as an untyped parameter and PostgreSQL rejects `? IS NULL`; select the
        // WHERE clause explicitly instead.
        boolean filtered = symbol != null && !symbol.isBlank();
        String sql = "SELECT t.trade_key, t.instrument_id, t.symbol, t.direction, t.entry_pattern, t.entry_at, "
                + "t.entry_price, t.initial_risk_per_unit, t.exit_at, t.exit_price, t.quantity, t.gross_pnl, t.explicit_costs, "
                + "t.net_pnl, t.realized_r, t.holding_seconds, t.exit_reason, t.ambiguous_bars, "
                + "t.cost_breakdown, t.plan_key, t.decision_key, t.mfe_r, t.mae_r "
                + "FROM research.backtest_trade t "
                + "JOIN research.backtest_run br ON br.backtest_run_id = t.backtest_run_id "
                + "WHERE br.run_key = ? " + (filtered ? "AND t.symbol = ? " : "")
                + "ORDER BY t.entry_at, t.backtest_trade_id LIMIT ? OFFSET ?";
        Object[] args = filtered
                ? new Object[] {UUID.fromString(runKey), symbol, limit, offset}
                : new Object[] {UUID.fromString(runKey), limit, offset};
        return dsl.fetch(sql, args)
                .map(record -> new BacktestTrade(
                        record.get("trade_key", UUID.class).toString(),
                        record.get("instrument_id", Long.class),
                        record.get("symbol", String.class),
                        com.edgerelative.application.strategy.domain.Direction.valueOf(record.get("direction", String.class)),
                        record.get("entry_pattern", String.class),
                        instant(record.get("entry_at", OffsetDateTime.class)),
                        record.get("entry_price", BigDecimal.class),
                        instant(record.get("exit_at", OffsetDateTime.class)),
                        record.get("exit_price", BigDecimal.class),
                        record.get("quantity", Long.class),
                        record.get("initial_risk_per_unit", BigDecimal.class),
                        record.get("gross_pnl", BigDecimal.class),
                        record.get("explicit_costs", BigDecimal.class),
                        record.get("net_pnl", BigDecimal.class),
                        record.get("realized_r", BigDecimal.class),
                        record.get("holding_seconds", Long.class),
                        record.get("exit_reason", String.class),
                        record.get("ambiguous_bars", Integer.class) == null ? 0 : record.get("ambiguous_bars", Integer.class),
                        readDecimalMap(record.get("cost_breakdown", String.class)),
                        record.get("plan_key", String.class),
                        record.get("decision_key", String.class),
                        record.get("mfe_r", BigDecimal.class),
                        record.get("mae_r", BigDecimal.class)));
    }

    public List<EquityPoint> findEquity(String runKey) {
        return dsl.fetch(
                        "SELECT e.at, e.equity, e.cash, e.gross_exposure, e.net_exposure, e.high_water, e.drawdown, "
                                + "e.drawdown_pct, e.open_positions FROM research.backtest_equity_point e "
                                + "JOIN research.backtest_run br ON br.backtest_run_id = e.backtest_run_id "
                                + "WHERE br.run_key = ? ORDER BY e.at",
                        UUID.fromString(runKey))
                .map(record -> new EquityPoint(
                        instant(record.get("at", OffsetDateTime.class)),
                        record.get("equity", BigDecimal.class),
                        record.get("cash", BigDecimal.class),
                        record.get("gross_exposure", BigDecimal.class),
                        record.get("net_exposure", BigDecimal.class),
                        record.get("high_water", BigDecimal.class),
                        record.get("drawdown", BigDecimal.class),
                        record.get("drawdown_pct", Double.class),
                        record.get("open_positions", Integer.class)));
    }

    public List<com.edgerelative.application.backtest.domain.BacktestRejection> findRejections(String runKey) {
        return dsl.fetch(
                        "SELECT r.at, r.instrument_id, r.direction, r.reason_code, r.detail "
                                + "FROM research.backtest_rejection r "
                                + "JOIN research.backtest_run br ON br.backtest_run_id = r.backtest_run_id "
                                + "WHERE br.run_key = ? ORDER BY r.at, r.backtest_rejection_id",
                        UUID.fromString(runKey))
                .map(record -> new com.edgerelative.application.backtest.domain.BacktestRejection(
                        instant(record.get("at", OffsetDateTime.class)),
                        record.get("instrument_id", Long.class),
                        com.edgerelative.application.strategy.domain.Direction.valueOf(record.get("direction", String.class)),
                        record.get("reason_code", String.class),
                        record.get("detail", String.class)));
    }

    /** Per-symbol completed-trade aggregates plus totals, so the UI never derives them from a page. */
    public List<Map<String, Object>> findSymbolAggregates(String runKey) {
        return dsl.fetch(
                        "SELECT t.symbol AS symbol, "
                                + "count(*) FILTER (WHERE t.exit_at IS NOT NULL) AS completed, "
                                + "count(*) FILTER (WHERE t.exit_at IS NOT NULL AND t.net_pnl > 0) AS wins, "
                                + "COALESCE(sum(t.net_pnl) FILTER (WHERE t.exit_at IS NOT NULL), 0) AS net, "
                                + "COALESCE(sum(t.explicit_costs) FILTER (WHERE t.exit_at IS NOT NULL), 0) AS costs, "
                                + "count(*) AS total "
                                + "FROM research.backtest_trade t "
                                + "JOIN research.backtest_run br ON br.backtest_run_id = t.backtest_run_id "
                                + "WHERE br.run_key = ? GROUP BY t.symbol ORDER BY net DESC",
                        UUID.fromString(runKey))
                .intoMaps();
    }

    public List<BacktestRunRow> listRuns(int limit) {
        return dsl.fetch(
                        "SELECT er.run_key, er.experiment_run_id, br.backtest_run_id, er.status, er.date_range_start, "
                                + "er.date_range_end, er.parameters, er.result_summary, er.progress_events, er.progress_total, "
                                + "er.progress_through, er.created_at, er.started_at, er.completed_at, "
                                + "br.universe_size, br.starting_capital, br.currency, br.metrics, er.failure, "
                                + "d.code AS dataset_code, dv.checksum AS dataset_checksum, "
                                + "s.code AS strategy_id, sv.version AS strategy_version "
                                + "FROM research.experiment_run er "
                                + "JOIN research.backtest_run br ON br.experiment_run_id = er.experiment_run_id "
                                + "JOIN research.dataset_version dv ON dv.dataset_version_id = br.dataset_version_id "
                                + "JOIN research.dataset d ON d.dataset_id = dv.dataset_id "
                                + "LEFT JOIN control.strategy_version sv ON sv.strategy_version_id = br.strategy_version_id "
                                + "LEFT JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
                                + "ORDER BY er.created_at DESC LIMIT ?",
                        limit)
                .map(this::mapRun);
    }

    public Optional<BacktestRunRow> findRun(String runKey) {
        Record record = dsl.fetchOne(
                "SELECT er.run_key, er.experiment_run_id, br.backtest_run_id, er.status, er.date_range_start, "
                        + "er.date_range_end, er.parameters, er.result_summary, er.progress_events, er.progress_total, "
                        + "er.progress_through, er.created_at, er.started_at, er.completed_at, "
                        + "br.universe_size, br.starting_capital, br.currency, br.metrics, er.failure, "
                        + "d.code AS dataset_code, dv.checksum AS dataset_checksum, "
                        + "s.code AS strategy_id, sv.version AS strategy_version "
                        + "FROM research.experiment_run er "
                        + "JOIN research.backtest_run br ON br.experiment_run_id = er.experiment_run_id "
                        + "JOIN research.dataset_version dv ON dv.dataset_version_id = br.dataset_version_id "
                        + "JOIN research.dataset d ON d.dataset_id = dv.dataset_id "
                        + "LEFT JOIN control.strategy_version sv ON sv.strategy_version_id = br.strategy_version_id "
                        + "LEFT JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
                        + "WHERE er.run_key = ?",
                UUID.fromString(runKey));
        return record == null ? Optional.empty() : Optional.of(mapRun(record));
    }

    private BacktestRunRow mapRun(Record r) {
        Long total = r.get("progress_total", Long.class);
        return new BacktestRunRow(
                r.get("run_key", UUID.class).toString(),
                r.get("experiment_run_id", Long.class),
                r.get("backtest_run_id", Long.class),
                r.get("status", String.class),
                r.get("strategy_id", String.class),
                r.get("strategy_version", Integer.class) == null ? null : "v" + r.get("strategy_version", Integer.class),
                r.get("dataset_code", String.class),
                r.get("dataset_checksum", String.class),
                r.get("date_range_start", LocalDate.class),
                r.get("date_range_end", LocalDate.class),
                r.get("universe_size", Integer.class) == null ? 0 : r.get("universe_size", Integer.class),
                r.get("starting_capital", BigDecimal.class),
                r.get("currency", String.class),
                r.get("progress_events", Long.class) == null ? 0 : r.get("progress_events", Long.class),
                total,
                instant(r.get("progress_through", OffsetDateTime.class)),
                instant(r.get("created_at", OffsetDateTime.class)),
                instant(r.get("started_at", OffsetDateTime.class)),
                instant(r.get("completed_at", OffsetDateTime.class)),
                readMap(r.get("metrics", String.class)),
                readMap(r.get("failure", String.class)),
                readMap(r.get("parameters", String.class)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> readDecimalMap(String value) {
        Map<String, Object> raw = readMap(value);
        Map<String, BigDecimal> result = new java.util.LinkedHashMap<>();
        raw.forEach((key, entry) -> result.put(key, entry == null ? null : new BigDecimal(String.valueOf(entry))));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return json.readValue(value, Map.class);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).toString();
    }
}
