package com.edgerelative.application.ml.application;

import com.edgerelative.application.backtest.engine.BacktestEngine;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.ml.domain.GbmModelArtifact;
import com.edgerelative.application.ml.domain.GbmModelEvaluator;
import com.edgerelative.application.ml.domain.MlFeatureVector;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import java.time.Instant;
import java.util.List;

/**
 * Advisory ranking overlay: scores a VALID, risk-approved candidate from its production feature
 * vector. It orders candidates; it never changes validity, approval or the concurrent-position cap.
 */
public final class MlRankingOverlay implements BacktestEngine.RankingOverlay {

    private final GbmModelArtifact model;
    private final MlFeatureVectorBuilder featureVectorBuilder;
    private final NseTradingCalendar calendar;
    private final int topK;
    private final GbmModelEvaluator evaluator = new GbmModelEvaluator();

    public MlRankingOverlay(
            GbmModelArtifact model,
            MlFeatureVectorBuilder featureVectorBuilder,
            NseTradingCalendar calendar,
            int topK) {
        this.model = model;
        this.featureVectorBuilder = featureVectorBuilder;
        this.calendar = calendar;
        this.topK = topK <= 0 ? Integer.MAX_VALUE : topK;
    }

    @Override
    public double score(
            FeatureSnapshot snapshot,
            StrategyEvaluationResult chosen,
            AggregatedCandle bar,
            Instant anchor,
            List<AggregatedCandle> history) {
        MlFeatureVector vector = featureVectorBuilder.build(snapshot, chosen, bar, anchor, history, calendar);
        return evaluator.score(model, vector);
    }

    @Override
    public int topK() {
        return topK;
    }
}
