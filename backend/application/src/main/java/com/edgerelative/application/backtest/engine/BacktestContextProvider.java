package com.edgerelative.application.backtest.engine;

import java.time.Instant;

/**
 * Supplies the market/stock context the production strategy needs at a simulated decision time.
 * Production producers are not wired yet, so the default {@link #strict()} provider returns
 * unavailable inputs and the strategy fails closed (no trades): a run then faithfully reflects the
 * currently supported production scope. A test fixture may supply deterministic inputs so the
 * execution/cost/accounting layers can be exercised end to end.
 */
public interface BacktestContextProvider {

    MarketInput market(long instrumentId, Instant at);

    StockInput stock(long instrumentId, Instant at);

    record MarketInput(boolean available, String bias, String regime, String phase) {
        public static MarketInput unavailable() {
            return new MarketInput(false, null, null, null);
        }
    }

    record StockInput(
            boolean available,
            String dailyStructure,
            String rssD1,
            String liquidityState,
            Double medianTradedValue,
            Double technicalVoidAtr,
            boolean eventRiskKnown,
            boolean eventRiskBlocked,
            StructureInput structure) {

        public static StockInput unavailable() {
            return new StockInput(false, null, null, null, null, null, false, false, null);
        }
    }

    /**
     * Structure producers are not wired in production; strict runs leave this null and the strategy
     * fails closed. A fixture may supply the EMA 3/8 relationship so the production 3-8 family and the
     * downstream risk/execution path can be exercised end to end.
     */
    record StructureInput(
            java.math.BigDecimal ema3,
            java.math.BigDecimal ema8,
            java.math.BigDecimal ema3Previous,
            java.math.BigDecimal ema8Previous) {
    }

    static BacktestContextProvider strict() {
        return new BacktestContextProvider() {
            @Override
            public MarketInput market(long instrumentId, Instant at) {
                return MarketInput.unavailable();
            }

            @Override
            public StockInput stock(long instrumentId, Instant at) {
                return StockInput.unavailable();
            }
        };
    }
}
