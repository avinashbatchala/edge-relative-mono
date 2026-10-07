package com.edgerelative.application.ml.application;

import com.edgerelative.application.backtest.application.BacktestService;
import com.edgerelative.application.backtest.domain.BacktestResult;
import com.edgerelative.application.backtest.domain.BacktestSpec;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.ml.application.MlTrainingExportService.ExportRequest;
import com.edgerelative.application.ml.domain.GbmModelArtifact;
import com.edgerelative.application.reference.NseTradingCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Verification backtest: replays the same window twice — deterministic baseline versus the ML ranking
 * overlay with a concurrent-position cap — and reports both so the operator can see whether ranking
 * changed the book. Read-only: it persists nothing and places no orders.
 */
@Service
public class MlVerificationService {

    private final BacktestService backtestService;
    private final BacktestEngine engine;
    private final MlArtifactLoader artifactLoader;
    private final MlFeatureVectorBuilder featureVectorBuilder;
    private final NseTradingCalendar calendar;

    public MlVerificationService(
            BacktestService backtestService,
            BacktestEngine engine,
            MlArtifactLoader artifactLoader,
            MlFeatureVectorBuilder featureVectorBuilder,
            NseTradingCalendar calendar) {
        this.backtestService = backtestService;
        this.engine = engine;
        this.artifactLoader = artifactLoader;
        this.featureVectorBuilder = featureVectorBuilder;
        this.calendar = calendar;
    }

    public record VerificationRequest(
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
            Long seed,
            long modelVersionId,
            Integer rankingTopK) {
    }

    public record SideMetrics(
            int completedTrades,
            int wins,
            BigDecimal netPnl,
            Double averageRealizedR,
            Map<String, Long> stageCounts) {
    }

    public record VerificationReport(SideMetrics baseline, SideMetrics ranked, int topK) {
    }

    public VerificationReport verify(VerificationRequest request) {
        ExportRequest export = new ExportRequest(
                request.symbols(), request.setupTimeframe(), request.dailyTimeframe(),
                request.startDate(), request.endDate(), request.marketSymbol(), request.startingCapital(),
                request.currency(), request.strategyPreset(), request.riskPreset(), request.contextSource(),
                request.seed());
        BacktestSpec spec = backtestService.resolveSpec(MlTrainingExportService.toBacktestRequest(export));

        BacktestResult baseline = engine.run(spec, null);
        GbmModelArtifact model = artifactLoader.load(request.modelVersionId());
        int topK = request.rankingTopK() == null ? Integer.MAX_VALUE : request.rankingTopK();
        BacktestEngine.RankingOverlay overlay =
                new MlRankingOverlay(model, featureVectorBuilder, calendar, topK);
        BacktestResult ranked = engine.run(spec, null, null, null, overlay);

        return new VerificationReport(side(baseline), side(ranked), topK);
    }

    private static SideMetrics side(BacktestResult result) {
        int completed = 0;
        int wins = 0;
        BigDecimal net = BigDecimal.ZERO;
        double sumR = 0.0;
        int rCount = 0;
        for (BacktestTrade trade : result.trades()) {
            if (trade.exitAt() != null) {
                completed++;
            }
            if (trade.netPnl() != null) {
                net = net.add(trade.netPnl());
                if (trade.netPnl().signum() > 0) {
                    wins++;
                }
            }
            if (trade.realizedR() != null) {
                sumR += trade.realizedR().doubleValue();
                rCount++;
            }
        }
        return new SideMetrics(completed, wins, net, rCount == 0 ? null : sumR / rCount, result.stageCounts());
    }
}
