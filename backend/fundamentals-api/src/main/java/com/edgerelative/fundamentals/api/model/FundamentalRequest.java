package com.edgerelative.fundamentals.api.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A point-in-time fundamentals lookup.
 *
 * @param exchange broker-neutral exchange code ({@code NSE} or {@code BSE})
 * @param symbol exchange trading symbol, for example {@code RELIANCE}
 * @param asOf only facts filed on or before this instant may be returned
 * @param basis desired reporting basis; {@code CONSOLIDATED} when unspecified
 */
public record FundamentalRequest(String exchange, String symbol, Instant asOf, ReportingBasis basis) {

    public FundamentalRequest {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("exchange must not be blank");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        Objects.requireNonNull(asOf, "asOf");
        if (basis == null) {
            basis = ReportingBasis.CONSOLIDATED;
        }
    }

    public static FundamentalRequest of(String exchange, String symbol, Instant asOf) {
        return new FundamentalRequest(exchange, symbol, asOf, ReportingBasis.CONSOLIDATED);
    }
}
