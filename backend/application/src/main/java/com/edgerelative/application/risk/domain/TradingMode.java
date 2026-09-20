package com.edgerelative.application.risk.domain;

/**
 * Trading mode (DD-01 authority boundaries, DD-03 §42). Modes are separated by capacity ledger so
 * research/paper can never consume live capacity.
 */
public enum TradingMode {
    /** No real-capital risk authority; no approval. */
    RESEARCH,
    BACKTEST,
    PAPER,
    ASSISTED_LIVE,
    GUARDED_AUTOPILOT;

    public boolean realCapital() {
        return this == ASSISTED_LIVE || this == GUARDED_AUTOPILOT;
    }
}
