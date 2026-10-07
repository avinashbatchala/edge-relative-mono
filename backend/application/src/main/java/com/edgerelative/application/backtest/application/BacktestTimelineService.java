package com.edgerelative.application.backtest.application;

import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.InstrumentTimeline;
import com.edgerelative.application.backtest.domain.InstrumentTimeline.TimelinePoint;
import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.backtest.persistence.BacktestRepository;
import com.edgerelative.application.feature.domain.ContextSnapshot;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reconstructs a run-scoped forensic timeline for one instrument by replaying the exact persisted
 * spec through the production engine. The same immutable spec plus canonical candles must yield the
 * same timeline; nothing is read from "current" state.
 */
@Service
public class BacktestTimelineService {

    private final BacktestRepository repository;
    private final BacktestEngine engine;
    private final JsonMapper json;

    public BacktestTimelineService(BacktestRepository repository, BacktestEngine engine, JsonMapper json) {
        this.repository = repository;
        this.engine = engine;
        this.json = json;
    }

    public InstrumentTimeline timeline(String runKey, long instrumentId) {
        BacktestSpec spec = repository.findSpec(runKey)
                .map(specJson -> {
                    try {
                        return json.readValue(specJson, BacktestSpec.class);
                    } catch (Exception malformed) {
                        throw new BacktestService.BacktestValidationException(
                                "Run " + runKey + " has no replayable spec: " + malformed.getMessage());
                    }
                })
                .orElseThrow(() -> new BacktestService.BacktestValidationException(
                        "Run " + runKey + " has no persisted spec."));

        int index = spec.instrumentIds().indexOf(instrumentId);
        String symbol = index >= 0 && index < spec.symbols().size()
                ? spec.symbols().get(index)
                : String.valueOf(instrumentId);

        List<TimelinePoint> points = new ArrayList<>();
        BacktestResult result = engine.run(spec, null, (id, anchor, bar, snapshot, longResult, shortResult) -> {
            if (id == instrumentId) {
                points.add(point(anchor, bar, snapshot, longResult, shortResult));
            }
        });
        List<com.edgerelative.application.backtest.domain.BacktestTrade> trades = result.trades().stream()
                .filter(trade -> trade.instrumentId() == instrumentId)
                .toList();
        return new InstrumentTimeline(instrumentId, symbol, points, trades);
    }

    private static TimelinePoint point(
            java.time.Instant anchor,
            AggregatedCandle bar,
            FeatureSnapshot snapshot,
            StrategyEvaluationResult longResult,
            StrategyEvaluationResult shortResult) {
        return new TimelinePoint(
                anchor,
                bar.open(),
                bar.high(),
                bar.low(),
                bar.close(),
                bar.volume(),
                value(snapshot, FeatureKeys.RRS_RAW),
                value(snapshot, FeatureKeys.RRS_FAST),
                value(snapshot, FeatureKeys.RRS_SLOW),
                value(snapshot, FeatureKeys.RRS_PERSISTENCE),
                value(snapshot, FeatureKeys.RRS_SLOPE),
                value(snapshot, FeatureKeys.RRS_ACCELERATION),
                value(snapshot, FeatureKeys.RRS_PERCENTILE),
                value(snapshot, FeatureKeys.RVOL_D1),
                value(snapshot, FeatureKeys.RVOL_INTERVAL),
                value(snapshot, FeatureKeys.RVOL_CUMULATIVE),
                value(snapshot, FeatureKeys.RVE),
                value(snapshot, FeatureKeys.ATR),
                label(snapshot == null ? null : snapshot.market(), FeatureKeys.MARKET_PRICE_STRUCTURE),
                value(snapshot == null ? null : snapshot.market(), FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY),
                value(snapshot == null ? null : snapshot.sector(), FeatureKeys.SECTOR_RRS_RAW),
                label(snapshot == null ? null : snapshot.sector(), FeatureKeys.SECTOR_PRICE_STRUCTURE),
                marketBias(longResult),
                marketRegime(longResult),
                longResult == null ? null : longResult.dailyStructure(),
                longResult == null ? null : longResult.rrsD1(),
                longResult == null ? null : longResult.liquidityState(),
                state(longResult),
                state(shortResult),
                longResult == null ? null : longResult.previousState().name(),
                longResult == null ? null : longResult.valid(),
                longResult == null ? null : longResult.transitioned(),
                reasons(longResult),
                reasons(shortResult),
                gates(longResult),
                gates(shortResult));
    }

    private static java.util.Map<String, String> gates(StrategyEvaluationResult result) {
        if (result == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> gates = new java.util.LinkedHashMap<>();
        result.hardGates().forEach(gate -> gates.put(gate.gateCode().name(), gate.status().name()));
        return gates;
    }

    private static String marketBias(StrategyEvaluationResult result) {
        return result == null ? null : result.marketBias();
    }

    private static String marketRegime(StrategyEvaluationResult result) {
        return result == null ? null : result.marketRegime();
    }

    private static String state(StrategyEvaluationResult result) {
        return result == null ? null : result.setupState().name();
    }

    private static List<String> reasons(StrategyEvaluationResult result) {
        return result == null
                ? List.of()
                : result.reasonCodes().stream().map(Enum::name).toList();
    }

    private static Double value(FeatureSnapshot snapshot, String key) {
        return number(snapshot == null ? null : snapshot.feature(key));
    }

    private static Double value(ContextSnapshot context, String key) {
        return number(context == null ? null : context.features().get(key));
    }

    private static Double number(FeatureValue value) {
        return value != null && value.availability() == FeatureAvailability.VALID ? value.value() : null;
    }

    private static String label(ContextSnapshot context, String key) {
        FeatureValue value = context == null ? null : context.features().get(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }
}
