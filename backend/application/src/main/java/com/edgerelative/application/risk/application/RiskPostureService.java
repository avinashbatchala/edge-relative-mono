package com.edgerelative.application.risk.application;

import com.edgerelative.application.risk.api.RiskPostureResponse;
import com.edgerelative.application.risk.persistence.RiskPostureRepository;
import com.edgerelative.application.risk.persistence.RiskPostureRepository.AccountRiskRow;
import com.edgerelative.application.risk.persistence.RiskPostureRepository.PortfolioRow;
import com.edgerelative.application.system.TradingControlStateRepository;
import com.edgerelative.application.system.TradingControlStateRepository.TradingControlState;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Assembles the authoritative risk posture for the Risk Center from persisted operational state, or
 * reports each block unavailable with a reason. Read-only and non-authorizing (DD-07 §106, honesty
 * rule §119): no zero is substituted for missing data.
 */
@Service
public class RiskPostureService {

    private final RiskPostureRepository repository;
    private final TradingControlStateRepository controlRepository;
    private final Clock clock;

    public RiskPostureService(
            RiskPostureRepository repository,
            TradingControlStateRepository controlRepository,
            Clock clock) {
        this.repository = repository;
        this.controlRepository = controlRepository;
        this.clock = clock;
    }

    public RiskPostureResponse current() {
        Instant asOf = clock.instant();
        List<String> unavailable = new ArrayList<>();

        Optional<PortfolioRow> portfolioRow = repository.latestPortfolio();
        if (portfolioRow.isEmpty()) {
            unavailable.add("No portfolio snapshot has been persisted (portfolio producer not wired).");
        }
        Optional<AccountRiskRow> accountRow = repository.latestAccountRisk();
        if (accountRow.isEmpty()) {
            unavailable.add("No authoritative risk account state exists for this session.");
        }
        Optional<TradingControlState> control = controlRepository.latest();
        if (control.isEmpty()) {
            unavailable.add("No trading control state row exists; schema defaults are shown.");
        }

        return new RiskPostureResponse(
                asOf,
                portfolioRow.isPresent(),
                portfolioRow.map(this::portfolio).orElse(null),
                accountRow.isPresent(),
                accountRow.map(this::account).orElse(null),
                control.map(this::controls)
                        .orElseGet(() -> new RiskPostureResponse.Controls(
                                false, false, false, false, false, false, null, null, null)),
                new RiskPostureResponse.Counts(
                        repository.openTradeCount(),
                        repository.activeReservationCount(),
                        repository.pendingOrderCount()),
                unavailable);
    }

    private RiskPostureResponse.Portfolio portfolio(PortfolioRow row) {
        return new RiskPostureResponse.Portfolio(
                row.snapshotAt(),
                row.tradingDate(),
                row.netLiquidationValue(),
                row.availableCash(),
                row.buyingPower(),
                row.marginUsed(),
                row.grossExposure(),
                row.netExposure(),
                row.openRisk(),
                row.stressOpenRisk(),
                row.realizedSessionPnl(),
                row.unrealizedPnl(),
                repository.openPositionCount());
    }

    private RiskPostureResponse.AccountRisk account(AccountRiskRow row) {
        return new RiskPostureResponse.AccountRisk(
                row.tradingDate(),
                row.riskState(),
                row.riskReferenceEquity(),
                row.currentNetLiquidationValue(),
                row.reservedRisk(),
                row.reservedNotional(),
                row.openRisk(),
                row.stressOpenRisk(),
                row.grossExposure(),
                row.netExposure(),
                row.realizedSessionPnl(),
                row.unrealizedPnl(),
                row.sessionDrawdown(),
                row.updatedAt());
    }

    private RiskPostureResponse.Controls controls(TradingControlState state) {
        return new RiskPostureResponse.Controls(
                true,
                state.stopNewTrades(),
                state.cancelPendingEntries(),
                state.flattenOnly(),
                state.automationEnabled(),
                state.executionEnabled(),
                state.reason(),
                state.updatedBy(),
                state.updatedAt());
    }
}
