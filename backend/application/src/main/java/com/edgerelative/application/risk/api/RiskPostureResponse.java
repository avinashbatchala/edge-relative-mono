package com.edgerelative.application.risk.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Authoritative, read-only risk posture for the operator Risk Center (DD-07 §106). Every block is
 * explicitly available or not; a missing producer yields {@code available=false} with a reason in
 * {@link #unavailable}, never a fabricated zero. This is reporting only: it grants no authority and
 * changes no state.
 */
public record RiskPostureResponse(
        Instant asOf,
        boolean portfolioAvailable,
        Portfolio portfolio,
        boolean accountRiskAvailable,
        AccountRisk account,
        Controls controls,
        Counts counts,
        List<String> unavailable) {

    public RiskPostureResponse {
        unavailable = unavailable == null ? List.of() : List.copyOf(unavailable);
    }

    /** Latest marked-to-market portfolio snapshot, when one has been persisted. */
    public record Portfolio(
            Instant snapshotAt,
            LocalDate tradingDate,
            BigDecimal netLiquidationValue,
            BigDecimal availableCash,
            BigDecimal buyingPower,
            BigDecimal marginUsed,
            BigDecimal grossExposure,
            BigDecimal netExposure,
            BigDecimal openRisk,
            BigDecimal stressOpenRisk,
            BigDecimal realizedSessionPnl,
            BigDecimal unrealizedPnl,
            int openPositions) {
    }

    /** Latest authoritative account risk state (risk-owned ledger). */
    public record AccountRisk(
            LocalDate tradingDate,
            String riskState,
            BigDecimal riskReferenceEquity,
            BigDecimal currentNetLiquidationValue,
            BigDecimal reservedRisk,
            BigDecimal reservedNotional,
            BigDecimal openRisk,
            BigDecimal stressOpenRisk,
            BigDecimal grossExposure,
            BigDecimal netExposure,
            BigDecimal realizedSessionPnl,
            BigDecimal unrealizedPnl,
            BigDecimal sessionDrawdown,
            Instant updatedAt) {
    }

    /** Persisted safety switches; {@code present=false} means none exists (schema defaults apply). */
    public record Controls(
            boolean present,
            boolean stopNewTrades,
            boolean cancelPendingEntries,
            boolean flattenOnly,
            boolean automationEnabled,
            boolean executionEnabled,
            String reason,
            String updatedBy,
            Instant updatedAt) {
    }

    public record Counts(int openTrades, int activeReservations, int pendingOrders) {
    }
}
