package com.edgerelative.application.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edgerelative.application.risk.domain.TradingMode;
import com.edgerelative.application.system.TradingControlStateRepository.TradingControlState;
import com.edgerelative.application.system.api.SystemModeResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SystemModeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void defaultsToResearchWhenNoControlStateExists() {
        TradingControlStateRepository repository = mock(TradingControlStateRepository.class);
        when(repository.latest()).thenReturn(Optional.empty());
        SystemModeService service = new SystemModeService(new TradingProperties(), repository, clock);

        SystemModeResponse response = service.current();

        assertThat(response.configuredMode()).isEqualTo("RESEARCH");
        assertThat(response.realCapital()).isFalse();
        assertThat(response.executionEnabled()).isFalse();
        assertThat(response.modeSource()).isEqualTo("default");
        assertThat(response.control().present()).isFalse();
        assertThat(response.asOf()).isEqualTo(NOW);
    }

    @Test
    void reportsPersistedControlStateAndExecutionEnablement() {
        TradingControlStateRepository repository = mock(TradingControlStateRepository.class);
        when(repository.latest()).thenReturn(Optional.of(new TradingControlState(
                true, false, false, false, true, "operator drill", "ops", NOW)));
        TradingProperties properties = new TradingProperties();
        properties.setMode(TradingMode.ASSISTED_LIVE);
        SystemModeService service = new SystemModeService(properties, repository, clock);

        SystemModeResponse response = service.current();

        assertThat(response.configuredMode()).isEqualTo("ASSISTED_LIVE");
        assertThat(response.realCapital()).isTrue();
        assertThat(response.executionEnabled()).isTrue();
        assertThat(response.modeSource()).isEqualTo("operational.trading_control_state");
        assertThat(response.control().present()).isTrue();
        assertThat(response.control().stopNewTrades()).isTrue();
        assertThat(response.control().reason()).isEqualTo("operator drill");
    }

    @Test
    void executionStaysDisabledWhenControlRowWithholdsIt() {
        TradingControlStateRepository repository = mock(TradingControlStateRepository.class);
        when(repository.latest()).thenReturn(Optional.of(new TradingControlState(
                false, false, false, true, false, null, null, NOW)));
        SystemModeService service = new SystemModeService(new TradingProperties(), repository, clock);

        assertThat(service.current().executionEnabled()).isFalse();
    }
}
