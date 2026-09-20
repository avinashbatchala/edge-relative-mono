package com.edgerelative.application.risk.domain;

import java.util.Comparator;

/**
 * Stable, versioned risk reason codes (DD-03 §158). Every code maps to a row in
 * {@code control.risk_reason_code}. Codes added beyond the DD-03 taxonomy are narrowly scoped and
 * seeded by migration V016.
 *
 * <p>{@code priority} gives a deterministic ordering so a decision always reports a stable primary
 * reason; all applicable failures are retained.
 */
public enum RiskReasonCode {
    // Authority / infrastructure safety (highest authority wins, DD-03 §219).
    COMPLIANCE_BLOCKED(RiskReasonType.REJECTION, 0),
    BROKER_HEALTH_BLOCKED(RiskReasonType.REJECTION, 1),
    RECONCILIATION_MISMATCH(RiskReasonType.REJECTION, 2),
    ORDER_STATE_UNKNOWN(RiskReasonType.REJECTION, 3),
    DATA_QUALITY_BLOCKED(RiskReasonType.REJECTION, 4),
    // Candidate / policy preconditions.
    INVALID_SETUP(RiskReasonType.REJECTION, 10),
    MISSING_POLICY(RiskReasonType.REJECTION, 11),
    MISSING_REQUIRED_INPUT(RiskReasonType.REJECTION, 12),
    RISK_STATE_BLOCKED(RiskReasonType.REJECTION, 13),
    SESSION_FLATTEN_WINDOW(RiskReasonType.REJECTION, 14),
    EVENT_RISK_BLOCKED(RiskReasonType.REJECTION, 15),
    // Drawdown / loss controls.
    DAILY_DRAWDOWN_LIMIT(RiskReasonType.REJECTION, 20),
    WEEKLY_DRAWDOWN_LIMIT(RiskReasonType.REJECTION, 21),
    MONTHLY_DRAWDOWN_LIMIT(RiskReasonType.REJECTION, 22),
    ACCOUNT_DRAWDOWN_LIMIT(RiskReasonType.REJECTION, 23),
    STRATEGY_DRAWDOWN_LIMIT(RiskReasonType.REJECTION, 24),
    CONSECUTIVE_LOSS_LIMIT(RiskReasonType.REJECTION, 25),
    // Sizing.
    INVALID_STOP_DISTANCE(RiskReasonType.REJECTION, 30),
    INSUFFICIENT_RISK_CAPACITY(RiskReasonType.REJECTION, 31),
    TRADE_RISK_LIMIT(RiskReasonType.REJECTION, 32),
    PORTFOLIO_OPEN_RISK_LIMIT(RiskReasonType.REJECTION, 33),
    PORTFOLIO_STRESS_RISK_LIMIT(RiskReasonType.REJECTION, 34),
    GROSS_EXPOSURE_LIMIT(RiskReasonType.REJECTION, 35),
    NET_EXPOSURE_LIMIT(RiskReasonType.REJECTION, 36),
    SYMBOL_CONCENTRATION_LIMIT(RiskReasonType.REJECTION, 37),
    SECTOR_CONCENTRATION_LIMIT(RiskReasonType.REJECTION, 38),
    CORRELATION_LIMIT(RiskReasonType.REJECTION, 39),
    MAX_POSITIONS_LIMIT(RiskReasonType.REJECTION, 40),
    LIQUIDITY_LIMIT(RiskReasonType.REJECTION, 41),
    SPREAD_LIMIT(RiskReasonType.REJECTION, 42),
    MARGIN_LIMIT(RiskReasonType.REJECTION, 43),
    BUYING_POWER_LIMIT(RiskReasonType.REJECTION, 44),
    PROHIBITED_ADDITION(RiskReasonType.REJECTION, 45),
    MAX_POSITION_NOTIONAL_LIMIT(RiskReasonType.REJECTION, 46),
    BROKER_QUANTITY_LIMIT(RiskReasonType.REJECTION, 47),
    // Reductions (applied, not fatal).
    RISK_REDUCED_DRAWDOWN(RiskReasonType.REDUCTION, 100),
    RISK_REDUCED_MARKET_REGIME(RiskReasonType.REDUCTION, 101),
    RISK_REDUCED_SECTOR_CONCENTRATION(RiskReasonType.REDUCTION, 102),
    RISK_REDUCED_CORRELATION(RiskReasonType.REDUCTION, 103),
    RISK_REDUCED_LIQUIDITY(RiskReasonType.REDUCTION, 104),
    RISK_REDUCED_MARGIN(RiskReasonType.REDUCTION, 105),
    RISK_REDUCED_DEPLOYMENT_STAGE(RiskReasonType.REDUCTION, 106),
    RISK_REDUCED_ML(RiskReasonType.REDUCTION, 107),
    RISK_REDUCED_MANUAL(RiskReasonType.REDUCTION, 108),
    RISK_REDUCED_CONSTRAINT_LIMIT(RiskReasonType.REDUCTION, 109),
    // Terminal authority outcomes.
    EXIT_REQUIRED(RiskReasonType.EXIT, 200),
    HALT_REQUIRED(RiskReasonType.HALT, 201),
    OTHER(RiskReasonType.INFO, 999);

    private final RiskReasonType type;
    private final int priority;

    RiskReasonCode(RiskReasonType type, int priority) {
        this.type = type;
        this.priority = priority;
    }

    public RiskReasonType type() {
        return type;
    }

    public int priority() {
        return priority;
    }

    public static Comparator<RiskReasonCode> deterministicOrder() {
        return Comparator.comparingInt(RiskReasonCode::priority).thenComparing(Enum::name);
    }
}
