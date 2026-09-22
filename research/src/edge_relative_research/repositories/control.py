"""Read adapters for the ``control`` schema.

Definitions are separate from immutable versions, so a research artifact can always be
resolved to the exact feature, feature-schema and strategy version that produced it.
"""

from __future__ import annotations

from typing import Any

from .base import RepositoryBase, where_clause
from .models import (
    FeatureDefinitionRow,
    FeatureSchemaMemberRow,
    FeatureSchemaVersionRow,
    FeatureVersionRow,
    RiskPolicyVersionRow,
    StrategyRow,
    StrategyVersionRow,
)

_STRATEGY_VERSION_COLUMNS = (
    "sv.strategy_version_id, sv.strategy_id, s.code AS strategy_code, sv.version, "
    "sv.lifecycle_state, sv.primary_timeframe_id, sv.feature_schema_version_id, "
    "sv.parameters, sv.code_version"
)


class ControlRepository(RepositoryBase):
    """Feature, schema and strategy version reads."""

    def feature_definitions(self, *, codes: list[str] | None = None) -> list[FeatureDefinitionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if codes:
            conditions.append("code = ANY(%s)")
            params.append(list(codes))
        sql = (
            "SELECT feature_definition_id, feature_key, code, name, description, value_type "
            "FROM control.feature_definition" + where_clause(conditions) + " ORDER BY code"
        )
        return self._many(FeatureDefinitionRow, "control.feature_definitions", sql, params)

    def feature_versions(
        self, *, feature_definition_id: int | None = None
    ) -> list[FeatureVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if feature_definition_id is not None:
            conditions.append("fv.feature_definition_id = %s")
            params.append(feature_definition_id)
        sql = (
            "SELECT fv.feature_version_id, fv.feature_definition_id, fd.code AS feature_code, "
            "fv.version, fv.calculation_version, fv.parameters, fv.implementation_reference, "
            "fv.code_version "
            "FROM control.feature_version fv "
            "JOIN control.feature_definition fd "
            "ON fd.feature_definition_id = fv.feature_definition_id"
            + where_clause(conditions)
            + " ORDER BY fd.code, fv.version"
        )
        return self._many(FeatureVersionRow, "control.feature_versions", sql, params)

    def feature_schema_versions(
        self, *, schema_code: str | None = None
    ) -> list[FeatureSchemaVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if schema_code is not None:
            conditions.append("fs.code = %s")
            params.append(schema_code)
        sql = (
            "SELECT fsv.feature_schema_version_id, fsv.feature_schema_id, "
            "fs.code AS schema_code, fsv.version, fsv.schema_hash "
            "FROM control.feature_schema_version fsv "
            "JOIN control.feature_schema fs ON fs.feature_schema_id = fsv.feature_schema_id"
            + where_clause(conditions)
            + " ORDER BY fs.code, fsv.version"
        )
        return self._many(FeatureSchemaVersionRow, "control.feature_schema_versions", sql, params)

    def feature_schema_members(
        self, feature_schema_version_id: int
    ) -> list[FeatureSchemaMemberRow]:
        sql = (
            "SELECT fsm.feature_schema_version_id, fsm.feature_version_id, "
            "fd.code AS feature_code, fsm.ordinal, fsm.alias "
            "FROM control.feature_schema_member fsm "
            "JOIN control.feature_version fv "
            "ON fv.feature_version_id = fsm.feature_version_id "
            "JOIN control.feature_definition fd "
            "ON fd.feature_definition_id = fv.feature_definition_id "
            "WHERE fsm.feature_schema_version_id = %s "
            "ORDER BY fsm.ordinal"
        )
        return self._many(
            FeatureSchemaMemberRow,
            "control.feature_schema_members",
            sql,
            [feature_schema_version_id],
        )

    def strategies(
        self, *, codes: list[str] | None = None, active_only: bool = False
    ) -> list[StrategyRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if codes:
            conditions.append("code = ANY(%s)")
            params.append(list(codes))
        if active_only:
            conditions.append("status = 'ACTIVE'")
        sql = (
            "SELECT strategy_id, strategy_key, code, name, setup_family, status "
            "FROM control.strategy" + where_clause(conditions) + " ORDER BY code"
        )
        return self._many(StrategyRow, "control.strategies", sql, params)

    def strategy_versions(
        self,
        *,
        strategy_code: str | None = None,
        lifecycle_states: list[str] | None = None,
    ) -> list[StrategyVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if strategy_code is not None:
            conditions.append("s.code = %s")
            params.append(strategy_code)
        if lifecycle_states:
            conditions.append("sv.lifecycle_state = ANY(%s)")
            params.append(list(lifecycle_states))
        sql = (
            f"SELECT {_STRATEGY_VERSION_COLUMNS} "
            "FROM control.strategy_version sv "
            "JOIN control.strategy s ON s.strategy_id = sv.strategy_id"
            + where_clause(conditions)
            + " ORDER BY s.code, sv.version DESC"
        )
        return self._many(StrategyVersionRow, "control.strategy_versions", sql, params)

    def strategy_version(self, strategy_version_id: int) -> StrategyVersionRow | None:
        sql = (
            f"SELECT {_STRATEGY_VERSION_COLUMNS} "
            "FROM control.strategy_version sv "
            "JOIN control.strategy s ON s.strategy_id = sv.strategy_id "
            "WHERE sv.strategy_version_id = %s"
        )
        return self._one(
            StrategyVersionRow,
            "control.strategy_version",
            sql,
            [strategy_version_id],
        )

    def risk_policy_versions(self, *, policy_code: str | None = None) -> list[RiskPolicyVersionRow]:
        conditions: list[str] = []
        params: list[Any] = []
        if policy_code is not None:
            conditions.append("rp.code = %s")
            params.append(policy_code)
        sql = (
            "SELECT rpv.risk_policy_version_id, rpv.risk_policy_id, rp.code AS policy_code, "
            "rpv.version, rpv.lifecycle_state, rpv.parameters, rpv.code_version "
            "FROM control.risk_policy_version rpv "
            "JOIN control.risk_policy rp ON rp.risk_policy_id = rpv.risk_policy_id"
            + where_clause(conditions)
            + " ORDER BY rp.code, rpv.version DESC"
        )
        return self._many(RiskPolicyVersionRow, "control.risk_policy_versions", sql, params)
