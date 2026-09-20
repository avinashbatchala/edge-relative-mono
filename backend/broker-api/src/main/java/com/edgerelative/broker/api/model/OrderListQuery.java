package com.edgerelative.broker.api.model;

/**
 * Pagination/filter for an order list query.
 */
public record OrderListQuery(BrokerSegment segment, Integer page, Integer pageSize) {
}
