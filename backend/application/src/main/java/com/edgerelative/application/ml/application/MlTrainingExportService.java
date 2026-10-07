package com.edgerelative.application.ml.application;

import com.edgerelative.application.backtest.application.BacktestRunRequest;
import com.edgerelative.application.backtest.application.BacktestService;
import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.ml.domain.MlFeatureVector;
import com.edgerelative.application.reference.NseTradingCalendar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Per-anchor training export: replays the requested window through the exact production engine and
 * emits, for every VALID setup that produced an approved plan, the production feature vector plus the
 * realized outcome when the plan filled. Labels come from completed trades, so anchors that never
 * filled carry no label and are excluded downstream — the exporter never fabricates an outcome.
 */
@Service
public class MlTrainingExportService {

    private final BacktestService backtestService;
    private final BacktestEngine engine;
    private final MlFeatureVectorBuilder featureVectorBuilder;
    private final NseTradingCalendar calendar;

    public MlTrainingExportService(
            BacktestService backtestService,
            BacktestEngine engine,
            MlFeatureVectorBuilder featureVectorBuilder,
            NseTradingCalendar calendar) {
        this.backtestService = backtestService;
        this.engine = engine;
        this.featureVectorBuilder = featureVectorBuilder;
        this.calendar = calendar;
    }

    public record ExportRequest(
            List<String> symbols,
            String setupTimeframe,
            String dailyTimeframe,
            LocalDate startDate,
            LocalDate endDate,
            String marketSymbol,
            BigDecimal startingCapital,
            String currency,
            String strategyPreset,
            String riskPreset,
            String contextSource,
            Long seed) {
    }

    public record Label(BigDecimal realizedR, BigDecimal mfeR, BigDecimal maeR, BigDecimal netPnl, String exitReason) {
    }

    public record AnchorRow(
            Instant anchorAt,
            LocalDate tradingDate,
            long instrumentId,
            String symbol,
            String direction,
            Map<String, Double> features,
            Label label,
            String planKey) {
    }

    public List<AnchorRow> export(ExportRequest request) {
        BacktestRunRequest backtest = toBacktestRequest(request);
        BacktestSpec spec = backtestService.resolveSpec(backtest);

        Map<Long, String> symbols = new HashMap<>();
        for (int index = 0; index < spec.instrumentIds().size(); index++) {
            symbols.put(spec.instrumentIds().get(index), spec.symbols().get(index));
        }

        List<Pending> pending = new ArrayList<>();
        BacktestResult result = engine.run(spec, null, null,
                (instrumentId, anchor, bar, snapshot, chosen, history, planKey) -> {
                    MlFeatureVector vector = featureVectorBuilder.build(snapshot, chosen, bar, anchor, history, calendar);
                    pending.add(new Pending(
                            planKey, instrumentId, anchor, calendar.sessionDate(anchor), chosen.direction().name(),
                            vector.values()));
                });

        Map<String, BacktestTrade> tradesByPlan = new HashMap<>();
        for (BacktestTrade trade : result.trades()) {
            if (trade.planKey() != null) {
                tradesByPlan.putIfAbsent(trade.planKey(), trade);
            }
        }

        List<AnchorRow> rows = new ArrayList<>(pending.size());
        for (Pending item : pending) {
            BacktestTrade trade = tradesByPlan.get(item.planKey());
            Label label = trade == null ? null : new Label(
                    trade.realizedR(), trade.mfeR(), trade.maeR(), trade.netPnl(), trade.exitReason());
            rows.add(new AnchorRow(
                    item.anchor(), item.tradingDate(), item.instrumentId(), symbols.get(item.instrumentId()),
                    item.direction(), item.features(), label, item.planKey()));
        }
        return rows;
    }

    static BacktestRunRequest toBacktestRequest(ExportRequest request) {
        return new BacktestRunRequest(
                request.symbols(),
                request.startDate(),
                request.endDate(),
                request.setupTimeframe() == null ? "M5" : request.setupTimeframe(),
                request.dailyTimeframe() == null ? "D1" : request.dailyTimeframe(),
                request.startingCapital() == null ? BigDecimal.valueOf(1_000_000) : request.startingCapital(),
                request.currency() == null ? "INR" : request.currency(),
                request.marketSymbol(),
                null,
                request.strategyPreset() == null ? "ER_RS_CONTINUATION_V1_RESEARCH" : request.strategyPreset(),
                null,
                request.riskPreset() == null ? "RESEARCH_PERMISSIVE" : request.riskPreset(),
                null, null, null,
                request.contextSource() == null ? "DERIVED_RESEARCH" : request.contextSource(),
                true, null, 60, request.seed() == null ? 7L : request.seed(),
                "MARK_TO_MARKET",
                new BacktestRunRequest.ExecutionRequest(
                        null, null, null, null, null, null, null, "TRIGGER_LIMIT", "R_MULTIPLE", 2.0, 1.5),
                null);
    }

    private record Pending(
            String planKey,
            long instrumentId,
            Instant anchor,
            LocalDate tradingDate,
            String direction,
            Map<String, Double> features) {
    }
}
