package com.edgerelative.broker.api.model;

/** Broker-neutral cancel-order intent (represented, not executable in this change). */
public record BrokerCancelOrderRequest(String brokerOrderId, BrokerSegment segment) {
}
