package com.edgerelative.application.strategy.application;

import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;

/** Published after a new, non-duplicate setup observation is appended (DD-04 §state events). */
public record SetupObservationRecorded(StrategyEvaluationResult result) {
}
