package com.edgerelative.application.history.api;

import com.edgerelative.broker.api.model.BrokerCandleInterval;
import java.time.Instant;

/** Requested historical range for one canonical instrument. */
public record StartBackfillRequest(
        long instrumentId, BrokerCandleInterval timeframe, Instant from, Instant to) {
}
