package com.edgerelative.broker.api.model;

/** Option greeks. {@code impliedVolatility} is a percentage as reported by the broker. */
public record BrokerOptionGreeks(
        double delta,
        double gamma,
        double theta,
        double vega,
        double rho,
        double impliedVolatility) {
}
