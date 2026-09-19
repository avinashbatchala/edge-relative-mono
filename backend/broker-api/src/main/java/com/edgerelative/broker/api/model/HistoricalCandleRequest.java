package com.edgerelative.broker.api.model;

import java.time.Instant;

/** Request for a single historical candle window. Ranges are split by the adapter when required. */
public record HistoricalCandleRequest(
        BrokerExchange exchange,
        BrokerSegment segment,
        String brokerSymbol,
        Instant startTime,
        Instant endTime,
        BrokerCandleInterval interval) {
}
