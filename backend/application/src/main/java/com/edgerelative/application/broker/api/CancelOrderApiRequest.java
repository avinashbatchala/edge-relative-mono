package com.edgerelative.application.broker.api;

/** Application contract for cancelling an order. Deliberately not executable in this change. */
public record CancelOrderApiRequest(String brokerOrderId, String segment) {
}
