package com.edgerelative.broker.api.model;

/** Pagination for trades belonging to an order. */
public record TradeListQuery(BrokerSegment segment, Integer page, Integer pageSize) {
}
