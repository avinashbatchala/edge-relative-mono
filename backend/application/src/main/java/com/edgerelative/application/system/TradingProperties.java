package com.edgerelative.application.system;

import com.edgerelative.application.risk.domain.TradingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator-declared trading mode. There is no runtime mode-switch endpoint yet, so the declared mode
 * is the authoritative source for the UI's mode badge; it defaults to {@link TradingMode#RESEARCH},
 * which holds no real-capital authority (DD-01 authority boundaries). Execution remains disabled
 * regardless of mode until a broker mutation path is enabled.
 */
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    private TradingMode mode = TradingMode.RESEARCH;

    public TradingMode getMode() {
        return mode;
    }

    public void setMode(TradingMode mode) {
        this.mode = mode == null ? TradingMode.RESEARCH : mode;
    }
}
