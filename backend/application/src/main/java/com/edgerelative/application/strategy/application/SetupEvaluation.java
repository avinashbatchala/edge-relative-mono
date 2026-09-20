package com.edgerelative.application.strategy.application;

import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;

/**
 * One evaluation outcome. {@code appended} is false when the semantic observation already existed,
 * in which case {@code result} is the deterministic (therefore identical) re-evaluation and no row
 * was written.
 */
public record SetupEvaluation(StrategyEvaluationResult result, boolean appended) {
}
