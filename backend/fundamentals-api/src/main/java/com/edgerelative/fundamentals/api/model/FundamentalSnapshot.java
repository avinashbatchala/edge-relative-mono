package com.edgerelative.fundamentals.api.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A point-in-time fundamental view for one instrument and one reporting period.
 *
 * <p>Advisory context only (DD-06 §2). It records its provider, source revision, and the {@code asOf}
 * cutoff so a caller can prove it did not see future filings.
 */
public record FundamentalSnapshot(
        String exchange,
        String symbol,
        String provider,
        String sourceRevision,
        Instant asOf,
        Filing filing,
        FinancialPeriod period,
        List<StatementLine> statements,
        List<FundamentalMetric> metrics) {

    public FundamentalSnapshot {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("exchange must not be blank");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(filing, "filing");
        Objects.requireNonNull(period, "period");
        statements = statements == null ? List.of() : List.copyOf(statements);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        if (filing.filedAt().isAfter(asOf)) {
            throw new IllegalArgumentException("filing timestamp is after the asOf cutoff");
        }
    }
}
