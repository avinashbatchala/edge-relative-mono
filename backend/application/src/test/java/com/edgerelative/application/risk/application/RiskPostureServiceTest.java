package com.edgerelative.application.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edgerelative.application.risk.api.RiskPostureResponse;
import com.edgerelative.application.risk.persistence.RiskPostureRepository;
import com.edgerelative.application.risk.persistence.RiskPostureRepository.AccountRiskRow;
import com.edgerelative.application.risk.persistence.RiskPostureRepository.PortfolioRow;
import com.edgerelative.application.system.TradingControlStateRepository;
import com.edgerelative.application.system.TradingControlStateRepository.TradingControlState;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RiskPostureServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void reportsEveryBlockUnavailableWhenNoStateExists() {
        RiskPostureRepository repository = mock(RiskPostureRepository.class);
        TradingControlStateRepository control = mock(TradingControlStateRepository.class);
        when(repository.latestPortfolio()).thenReturn(Optional.empty());
        when(repository.latestAccountRisk()).thenReturn(Optional.empty());
        when(control.latest()).thenReturn(Optional.empty());
        RiskPostureService service = new RiskPostureService(repository, control, clock);

        RiskPostureResponse response = service.current();

        assertThat(response.portfolioAvailable()).isFalse();
        assertThat(response.portfolio()).isNull();
        assertThat(response.accountRiskAvailable()).isFalse();
        assertThat(response.account()).isNull();
        assertThat(response.controls().present()).isFalse();
        assertThat(response.unavailable()).hasSize(3);
    }

    @Test
    void mapsPersistedPortfolioAccountAndControlState() {
        RiskPostureRepository repository = mock(RiskPostureRepository.class);
        TradingControlStateRepository control = mock(TradingControlStateRepository.class);
        when(repository.latestPortfolio()).thenReturn(Optional.of(new PortfolioRow(
                NOW, DAY, new BigDecimal("1000000"), new BigDecimal("250000"), new BigDecimal("500000"),
                new BigDecimal("100000"), new BigDecimal("400000"), new BigDecimal("400000"),
                new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("0"), new BigDecimal("1500"))));
        when(repository.latestAccountRisk()).thenReturn(Optional.of(new AccountRiskRow(
                DAY, "REDUCED_1", new BigDecimal("1000000"), new BigDecimal("998000"),
                new BigDecimal("4000"), new BigDecimal("300000"), new BigDecimal("5000"),
                new BigDecimal("8000"), new BigDecimal("400000"), new BigDecimal("400000"),
                new BigDecimal("0"), new BigDecimal("1500"), new BigDecimal("2000"), NOW)));
        when(repository.openTradeCount()).thenReturn(2);
        when(repository.activeReservationCount()).thenReturn(1);
        when(repository.pendingOrderCount()).thenReturn(3);
        when(repository.openPositionCount()).thenReturn(2);
        when(control.latest()).thenReturn(Optional.of(new TradingControlState(
                false, false, false, false, false, null, null, NOW)));
        RiskPostureService service = new RiskPostureService(repository, control, clock);

        RiskPostureResponse response = service.current();

        assertThat(response.portfolioAvailable()).isTrue();
        assertThat(response.portfolio().netLiquidationValue()).isEqualByComparingTo("1000000");
        assertThat(response.portfolio().openPositions()).isEqualTo(2);
        assertThat(response.accountRiskAvailable()).isTrue();
        assertThat(response.account().riskState()).isEqualTo("REDUCED_1");
        assertThat(response.controls().present()).isTrue();
        assertThat(response.counts().openTrades()).isEqualTo(2);
        assertThat(response.counts().activeReservations()).isEqualTo(1);
        assertThat(response.counts().pendingOrders()).isEqualTo(3);
        assertThat(response.unavailable()).isEmpty();
    }
}
