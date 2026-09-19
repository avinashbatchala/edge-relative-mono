package com.edgerelative.broker.api.model;

/**
 * Broker acknowledgement of a mutated order.
 *
 * <p>Returned only by implementations that are permitted to execute. The disabled adapter never
 * produces one.
 */
public record BrokerOrderReference(String brokerOrderId, String orderReferenceId, BrokerOrderStatus status) {
}
