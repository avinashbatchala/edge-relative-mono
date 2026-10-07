package com.edgerelative.application.history;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource bounds for server-side replay/batch reads (backtest, dataset manifest).
 *
 * <p>Unlike the interactive history endpoint, which is capped to keep a single request small, a
 * controlled server-side backtest may legitimately read many years of canonical M1. These caps are
 * deliberately generous but still bounded so a malformed request cannot exhaust memory.
 */
@ConfigurationProperties(prefix = "history.replay")
public class HistoryReplayProperties {

    /** Maximum derived bars returned to a replay caller. */
    private int maxBars = 2_000_000;
    /** Maximum canonical M1 rows loaded to derive a higher-timeframe replay series. */
    private int maxSourceCandles = 3_000_000;

    public int getMaxBars() {
        return maxBars;
    }

    public void setMaxBars(int maxBars) {
        this.maxBars = maxBars;
    }

    public int getMaxSourceCandles() {
        return maxSourceCandles;
    }

    public void setMaxSourceCandles(int maxSourceCandles) {
        this.maxSourceCandles = maxSourceCandles;
    }

    public void validate() {
        if (maxBars < 1) {
            throw new IllegalStateException("history.replay.max-bars must be >= 1");
        }
        if (maxSourceCandles < 1) {
            throw new IllegalStateException("history.replay.max-source-candles must be >= 1");
        }
    }
}
