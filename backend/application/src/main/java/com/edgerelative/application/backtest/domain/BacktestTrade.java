package com.edgerelative.application.backtest.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One completed or open simulated trade. Prices are historical tradable prices (not adjusted
 * analytical series). {@code explicitCosts} are itemized fees; slippage is already reflected in the
 * fill prices and is never deducted twice.
 */
public record BacktestTrade(
        String tradeKey,
        long instrumentId,
        String symbol,
        Direction direction,
        String entryPattern,
        Instant entryAt,
        BigDecimal entryPrice,
        Instant exitAt,
        BigDecimal exitPrice,
        long quantity,
        BigDecimal initialRiskPerUnit,
        BigDecimal grossPnl,
        BigDecimal explicitCosts,
        BigDecimal netPnl,
        BigDecimal realizedR,
        Long holdingSeconds,
        String exitReason,
        int ambiguousBars,
        Map<String, BigDecimal> costBreakdown,
        String planKey,
        String decisionKey,
        BigDecimal mfeR,
        BigDecimal maeR) {

    public BacktestTrade {
        costBreakdown = costBreakdown == null ? Map.of() : Map.copyOf(costBreakdown);
    }

    public boolean isOpen() {
        return exitAt == null;
    }
}
