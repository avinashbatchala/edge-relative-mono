package com.edgerelative.application.risk.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries over the persisted operational risk/portfolio state for the Risk Center. These
 * tables are written by authoritative producers (which are not yet wired); when a row is absent the
 * caller reports unavailability rather than substituting zero.
 */
@Repository
public class RiskPostureRepository {

    private final DSLContext dsl;

    public RiskPostureRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<PortfolioRow> latestPortfolio() {
        Record row = dsl.fetchOne(
                "SELECT snapshot_at, trading_date, net_liquidation_value, available_cash, buying_power, "
                        + "margin_used, gross_exposure, net_exposure, open_risk, stress_open_risk, "
                        + "realized_session_pnl, unrealized_pnl "
                        + "FROM operational.portfolio_snapshot ORDER BY snapshot_at DESC LIMIT 1");
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new PortfolioRow(
                row.get("snapshot_at", Instant.class),
                row.get("trading_date", LocalDate.class),
                row.get("net_liquidation_value", BigDecimal.class),
                row.get("available_cash", BigDecimal.class),
                row.get("buying_power", BigDecimal.class),
                row.get("margin_used", BigDecimal.class),
                row.get("gross_exposure", BigDecimal.class),
                row.get("net_exposure", BigDecimal.class),
                row.get("open_risk", BigDecimal.class),
                row.get("stress_open_risk", BigDecimal.class),
                row.get("realized_session_pnl", BigDecimal.class),
                row.get("unrealized_pnl", BigDecimal.class)));
    }

    public Optional<AccountRiskRow> latestAccountRisk() {
        Record row = dsl.fetchOne(
                "SELECT trading_date, risk_state, risk_reference_equity, current_net_liquidation_value, "
                        + "reserved_risk, reserved_notional, open_risk, stress_open_risk, gross_exposure, "
                        + "net_exposure, realized_session_pnl, unrealized_pnl, session_drawdown, updated_at "
                        + "FROM operational.risk_account_state ORDER BY updated_at DESC LIMIT 1");
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new AccountRiskRow(
                row.get("trading_date", LocalDate.class),
                row.get("risk_state", String.class),
                row.get("risk_reference_equity", BigDecimal.class),
                row.get("current_net_liquidation_value", BigDecimal.class),
                row.get("reserved_risk", BigDecimal.class),
                row.get("reserved_notional", BigDecimal.class),
                row.get("open_risk", BigDecimal.class),
                row.get("stress_open_risk", BigDecimal.class),
                row.get("gross_exposure", BigDecimal.class),
                row.get("net_exposure", BigDecimal.class),
                row.get("realized_session_pnl", BigDecimal.class),
                row.get("unrealized_pnl", BigDecimal.class),
                row.get("session_drawdown", BigDecimal.class),
                row.get("updated_at", Instant.class)));
    }

    public int openTradeCount() {
        Record row = dsl.fetchOne(
                "SELECT count(*) AS c FROM operational.trade "
                        + "WHERE status IN ('OPENING','OPEN','REDUCING','CLOSING')");
        return row == null ? 0 : row.get("c", Integer.class);
    }

    public int activeReservationCount() {
        Record row = dsl.fetchOne(
                "SELECT count(*) AS c FROM operational.risk_reservation "
                        + "WHERE status IN ('ACTIVE','PARTIALLY_CONSUMED','CANCEL_PENDING')");
        return row == null ? 0 : row.get("c", Integer.class);
    }

    public int openPositionCount() {
        Record row = dsl.fetchOne(
                "SELECT count(*) AS c FROM operational.position_projection WHERE quantity <> 0");
        return row == null ? 0 : row.get("c", Integer.class);
    }

    public int pendingOrderCount() {
        Record row = dsl.fetchOne(
                "SELECT count(*) AS c FROM operational.order_record "
                        + "WHERE status IN ('CREATED','SUBMITTING','SUBMITTED','ACKNOWLEDGED','PARTIAL','CANCEL_PENDING')");
        return row == null ? 0 : row.get("c", Integer.class);
    }

    public record PortfolioRow(
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
            BigDecimal unrealizedPnl) {
    }

    public record AccountRiskRow(
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
}
