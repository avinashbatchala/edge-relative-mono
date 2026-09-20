package com.edgerelative.application.backtest.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Marked-to-market portfolio equity sample. Drawdown is computed from net equity, including the
 * starting capital in the high-water-mark series. */
public record EquityPoint(
        Instant at,
        BigDecimal equity,
        BigDecimal cash,
        BigDecimal grossExposure,
        BigDecimal netExposure,
        BigDecimal highWater,
        BigDecimal drawdown,
        Double drawdownPct,
        int openPositions) {
}
