package com.edgerelative.application.system;

import com.edgerelative.application.risk.domain.TradingMode;
import com.edgerelative.application.system.TradingControlStateRepository.TradingControlState;
import com.edgerelative.application.system.api.SystemModeResponse;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Assembles the authoritative current mode and control posture. Read-only and non-authorizing: it
 * reports declared configuration and persisted controls, never a decision to trade.
 */
@Service
public class SystemModeService {

    private final TradingProperties properties;
    private final TradingControlStateRepository controlRepository;
    private final Clock clock;

    public SystemModeService(
            TradingProperties properties, TradingControlStateRepository controlRepository, Clock clock) {
        this.properties = properties;
        this.controlRepository = controlRepository;
        this.clock = clock;
    }

    public SystemModeResponse current() {
        TradingMode mode = properties.getMode();
        Optional<TradingControlState> control = controlRepository.latest();
        SystemModeResponse.Control controlView = control
                .map(state -> new SystemModeResponse.Control(
                        state.stopNewTrades(),
                        state.cancelPendingEntries(),
                        state.flattenOnly(),
                        state.automationEnabled(),
                        state.executionEnabled(),
                        state.reason(),
                        state.updatedBy(),
                        state.updatedAt(),
                        true))
                .orElseGet(() -> new SystemModeResponse.Control(
                        false, false, false, false, false, null, null, null, false));
        // Execution is only ever enabled when the persisted control row says so; otherwise the
        // product remains read-only regardless of declared mode.
        boolean executionEnabled = controlView.present() && controlView.executionEnabled();
        return new SystemModeResponse(
                mode.name(),
                mode.realCapital(),
                executionEnabled,
                controlView,
                control.isPresent() ? "operational.trading_control_state" : "default",
                clock.instant());
    }
}
