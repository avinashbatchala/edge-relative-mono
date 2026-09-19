package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** Open/high/low/close snapshot. */
public record BrokerOhlc(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
}
