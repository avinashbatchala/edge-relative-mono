package com.edgerelative.application.backtest.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API request to start (or idempotently re-request) a backtest run. */
public record BacktestRunRequest(
        List<String> symbols,
        LocalDate startDate,
        LocalDate endDate,
        String timeframe,
        String dailyTimeframe,
        BigDecimal startingCapital,
        String currency,
        String marketSymbol,
        String sectorSymbol,
        String strategyPreset,
        String riskPreset,
        String riskPolicyCode,
        Long strategyVersionId,
        Long riskPolicyVersionId,
        String contextSource,
        // Boxed so an omitted optional field is accepted rather than rejected by Jackson's
        // FAIL_ON_NULL_FOR_PRIMITIVES; defaults are applied in the service.
        Boolean strictProducers,
        Integer warmupBars,
        Integer warmupSessions,
        Long seed,
        String endOfRun,
        ExecutionRequest execution,
        CostRequest costs) {

    public record ExecutionRequest(
            String version,
            Double halfSpreadBps,
            Double adverseSlippageBps,
            Double participationRate,
            Integer orderExpiryBars,
            String ambiguityPolicy,
            Boolean allowOvernight) {
    }

    public record CostRequest(
            String version,
            Double brokerageBuyBps,
            Double brokerageSellBps,
            Double sttSellBps,
            Double exchangeBps,
            Double gstBps,
            Double sebiBps,
            Double stampDutyBuyBps,
            Double otherBuyBps,
            Double otherSellBps) {
    }
}
