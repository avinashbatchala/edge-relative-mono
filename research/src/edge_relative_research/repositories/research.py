"""Read adapters for the ``research`` schema (dataset, outcome, pattern, experiment).

Python may write this schema, but this stage implements reads only; dataset creation and
manifest lifecycle belong to the reproducibility stage.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from .base import RepositoryBase, where_clause
from .models import (
    BacktestRunRow,
    DatasetRow,
    DatasetVersionInputRow,
    DatasetVersionRow,
    ExperimentRow,
    ExperimentRunRow,
    ModelCandidateRow,
    OutcomeDefinitionRow,
    OutcomeSchemaVersionRow,
    PatternSchemaVersionRow,
    PatternWindowRow,
    SimilarityIndexRow,
    TrainingRunRow,
)


class ResearchRepository(RepositoryBase):
    """Dataset, outcome, pattern and experiment metadata reads."""

    def datasets(self, *, code: str | None = None) -> list[DatasetRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if code is not None:
            conditions.append("code = %s")
            params.append(code)
        sql = (
            "SELECT dataset_id, dataset_key, code, name, dataset_type, description "
            "FROM research.dataset" + where_clause(conditions) + " ORDER BY code"
        )
        return self._many(DatasetRow, "research.datasets", sql, params)

    def dataset_versions(
        self,
        *,
        dataset_code: str | None = None,
        status: str | None = None,
    ) -> list[DatasetVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if dataset_code is not None:
            conditions.append("d.code = %s")
            params.append(dataset_code)
        if status is not None:
            conditions.append("dv.status = %s")
            params.append(status)
        sql = (
            "SELECT dv.dataset_version_id, dv.dataset_version_key, dv.dataset_id, "
            "d.code AS dataset_code, dv.version, dv.status, dv.feature_schema_version_id, "
            "dv.outcome_schema_version_id, dv.point_in_time_cutoff, dv.universe, "
            "dv.start_timestamp, dv.end_timestamp, dv.storage_uri, dv.partition_manifest_uri, "
            "dv.row_count, dv.checksum, dv.code_version, dv.build_parameters, dv.failure, "
            "dv.committed_at, dv.retired_at, dv.created_at "
            "FROM research.dataset_version dv "
            "JOIN research.dataset d ON d.dataset_id = dv.dataset_id"
            + where_clause(conditions)
            + " ORDER BY d.code, dv.version"
        )
        return self._many(DatasetVersionRow, "research.dataset_versions", sql, params)

    def dataset_version_inputs(self, dataset_version_id: int) -> list[DatasetVersionInputRow]:
        sql = (
            "SELECT dataset_version_id, input_dataset_version_id, input_role "
            "FROM research.dataset_version_input WHERE dataset_version_id = %s "
            "ORDER BY input_role, input_dataset_version_id"
        )
        return self._many(
            DatasetVersionInputRow,
            "research.dataset_version_inputs",
            sql,
            [dataset_version_id],
        )

    def outcome_schema_versions(
        self, *, schema_code: str | None = None
    ) -> list[OutcomeSchemaVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if schema_code is not None:
            conditions.append("os.code = %s")
            params.append(schema_code)
        sql = (
            "SELECT osv.outcome_schema_version_id, osv.outcome_schema_id, "
            "os.code AS schema_code, osv.version "
            "FROM research.outcome_schema_version osv "
            "JOIN research.outcome_schema os ON os.outcome_schema_id = osv.outcome_schema_id"
            + where_clause(conditions)
            + " ORDER BY os.code, osv.version"
        )
        return self._many(OutcomeSchemaVersionRow, "research.outcome_schema_versions", sql, params)

    def outcome_definitions(
        self, *, outcome_schema_version_id: int | None = None
    ) -> list[OutcomeDefinitionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if outcome_schema_version_id is not None:
            conditions.append("outcome_schema_version_id = %s")
            params.append(outcome_schema_version_id)
        sql = (
            "SELECT outcome_definition_id, outcome_schema_version_id, code, value_type, "
            "horizon_seconds, parameters, ordinal FROM research.outcome_definition"
            + where_clause(conditions)
            + " ORDER BY outcome_schema_version_id, ordinal"
        )
        return self._many(OutcomeDefinitionRow, "research.outcome_definitions", sql, params)

    def pattern_schema_versions(
        self, *, schema_code: str | None = None
    ) -> list[PatternSchemaVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if schema_code is not None:
            conditions.append("ps.code = %s")
            params.append(schema_code)
        sql = (
            "SELECT psv.pattern_schema_version_id, psv.pattern_schema_id, "
            "ps.code AS schema_code, psv.version, psv.timeframe_id, psv.window_length, "
            "psv.feature_schema_version_id, psv.parameters "
            "FROM research.pattern_schema_version psv "
            "JOIN research.pattern_schema ps "
            "ON ps.pattern_schema_id = psv.pattern_schema_id"
            + where_clause(conditions)
            + " ORDER BY ps.code, psv.version"
        )
        return self._many(PatternSchemaVersionRow, "research.pattern_schema_versions", sql, params)

    def pattern_windows(
        self,
        *,
        instrument_id: int | None = None,
        from_timestamp: datetime | None = None,
        to_timestamp: datetime | None = None,
        limit: int | None = None,
    ) -> list[PatternWindowRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if instrument_id is not None:
            conditions.append("instrument_id = %s")
            params.append(instrument_id)
        if from_timestamp is not None:
            conditions.append("anchor_timestamp >= %s")
            params.append(from_timestamp)
        if to_timestamp is not None:
            conditions.append("anchor_timestamp < %s")
            params.append(to_timestamp)
        sql = (
            "SELECT pattern_window_metadata_id, pattern_key, pattern_schema_version_id, "
            "dataset_version_id, market_observation_id, instrument_id, sector_id, "
            "timeframe_id, anchor_timestamp, market_regime, sector_regime, "
            "normalization_version, minutes_since_open, storage_uri, storage_row_reference "
            "FROM research.pattern_window_metadata"
            + where_clause(conditions)
            + " ORDER BY anchor_timestamp, pattern_window_metadata_id"
        )
        if limit is not None:
            sql += " LIMIT %s"
            params.append(limit)
        return self._many(PatternWindowRow, "research.pattern_windows", sql, params)

    def similarity_indexes(self, *, status: str | None = None) -> list[SimilarityIndexRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if status is not None:
            conditions.append("status = %s")
            params.append(status)
        sql = (
            "SELECT similarity_index_metadata_id, index_key, pattern_schema_version_id, "
            "dataset_version_id, index_type, status, corpus_cutoff, storage_uri, checksum "
            "FROM research.similarity_index_metadata"
            + where_clause(conditions)
            + " ORDER BY corpus_cutoff DESC"
        )
        return self._many(SimilarityIndexRow, "research.similarity_indexes", sql, params)

    def experiments(self, *, code: str | None = None) -> list[ExperimentRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if code is not None:
            conditions.append("code = %s")
            params.append(code)
        sql = (
            "SELECT experiment_id, experiment_key, code, hypothesis, created_by "
            "FROM research.experiment" + where_clause(conditions) + " ORDER BY code"
        )
        return self._many(ExperimentRow, "research.experiments", sql, params)

    def experiment_runs(
        self,
        *,
        experiment_code: str | None = None,
        status: str | None = None,
    ) -> list[ExperimentRunRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if experiment_code is not None:
            conditions.append("e.code = %s")
            params.append(experiment_code)
        if status is not None:
            conditions.append("er.status = %s")
            params.append(status)
        sql = (
            "SELECT er.experiment_run_id, er.run_key, er.experiment_id, "
            "e.code AS experiment_code, er.status, er.strategy_version_id, "
            "er.dataset_version_id, er.date_range_start, er.date_range_end, er.parameters, "
            "er.cost_model, er.result_summary, er.code_version, er.started_at, "
            "er.completed_at "
            "FROM research.experiment_run er "
            "JOIN research.experiment e ON e.experiment_id = er.experiment_id"
            + where_clause(conditions)
            + " ORDER BY er.created_at DESC"
        )
        return self._many(ExperimentRunRow, "research.experiment_runs", sql, params)

    def backtest_runs(self, *, experiment_run_id: int | None = None) -> list[BacktestRunRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if experiment_run_id is not None:
            conditions.append("experiment_run_id = %s")
            params.append(experiment_run_id)
        sql = (
            "SELECT backtest_run_id, run_key, experiment_run_id, strategy_version_id, "
            "risk_policy_version_id, dataset_version_id, status, result_uri, metrics, "
            "seed, starting_capital, currency, engine_revision "
            "FROM research.backtest_run" + where_clause(conditions) + " ORDER BY created_at DESC"
        )
        return self._many(BacktestRunRow, "research.backtest_runs", sql, params)

    def training_runs(self) -> list[TrainingRunRow]:
        sql = (
            "SELECT training_run_id, run_key, experiment_run_id, dataset_version_id, "
            "feature_schema_version_id, status, algorithm, metrics, artifact_uri "
            "FROM research.training_run ORDER BY created_at DESC"
        )
        return self._many(TrainingRunRow, "research.training_runs", sql)

    def model_candidates(self, *, status: str | None = None) -> list[ModelCandidateRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if status is not None:
            conditions.append("status = %s")
            params.append(status)
        sql = (
            "SELECT model_candidate_id, candidate_key, model_id, training_run_id, status, "
            "artifact_uri, metrics, promoted_model_version_id FROM research.model_candidate"
            + where_clause(conditions)
            + " ORDER BY created_at DESC"
        )
        return self._many(ModelCandidateRow, "research.model_candidates", sql, params)
