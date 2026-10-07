package com.edgerelative.application.backtest.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        // Inline, validated strategy parameters for automated research sweeps. Takes precedence over
        // strategyPreset/strategyVersionId and is recorded in the run manifest.
        Map<String, Object> strategyParameters,
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

    /** Copy this request with a different inline parameter set (used by sweeps). */
    public BacktestRunRequest withStrategyParameters(Map<String, Object> parameters) {
        return new BacktestRunRequest(
                symbols, startDate, endDate, timeframe, dailyTimeframe, startingCapital, currency, marketSymbol,
                sectorSymbol, strategyPreset, parameters, riskPreset, riskPolicyCode, strategyVersionId,
                riskPolicyVersionId, contextSource, strictProducers, warmupBars, warmupSessions, seed, endOfRun,
                execution, costs);
    }

    public record ExecutionRequest(
            String version,
            Double halfSpreadBps,
            Double adverseSlippageBps,
            Double participationRate,
            Integer orderExpiryBars,
            String ambiguityPolicy,
            Boolean allowOvernight,
            String entryMethod,
            String targetMethod,
            Double targetR,
            Double minStopAtr) {
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
