package com.edgerelative.application.backtest.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.time.Instant;

/** A candidate that was evaluated but did not produce an approved plan, with its reason. */
public record BacktestRejection(
        Instant at, long instrumentId, Direction direction, String reasonCode, String detail) {
}
