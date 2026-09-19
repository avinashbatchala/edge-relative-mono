package com.edgerelative.broker.api.model;

import java.math.BigDecimal;

/** One side of the order book at a given level. */
public record BrokerDepthLevel(BigDecimal price, long quantity) {
}
