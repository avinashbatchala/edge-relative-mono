package com.edgerelative.broker.api.model;

/**
 * Normalised order lifecycle status.
 */
public enum BrokerOrderStatus {
    NEW,
    ACKED,
    TRIGGER_PENDING,
    APPROVED,
    REJECTED,
    FAILED,
    EXECUTED,
    DELIVERY_AWAITED,
    CANCELLED,
    CANCELLATION_REQUESTED,
    MODIFICATION_REQUESTED,
    COMPLETED,
    UNKNOWN
}
