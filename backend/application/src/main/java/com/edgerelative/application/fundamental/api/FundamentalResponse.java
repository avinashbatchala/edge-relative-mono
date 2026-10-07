package com.edgerelative.application.fundamental.api;

import com.edgerelative.fundamentals.api.model.FundamentalMetric;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;
import com.edgerelative.fundamentals.api.model.StatementLine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Point-in-time fundamental response. {@code advisory} is always true: this is context, never a
 * trading decision (DD-06 §2).
 */
public record FundamentalResponse(
        boolean advisory,
        long instrumentId,
        String exchange,
        String symbol,
        String provider,
        String sourceRevision,
        Instant asOf,
        Instant filedAt,
        String fiscalYear,
        String periodType,
        String reportingBasis,
        LocalDate periodEnd,
        List<StatementLineResponse> statements,
        List<MetricResponse> metrics) {

    public static FundamentalResponse from(long instrumentId, FundamentalSnapshot snapshot) {
        return new FundamentalResponse(
                true,
                instrumentId,
                snapshot.exchange(),
                snapshot.symbol(),
                snapshot.provider(),
                snapshot.sourceRevision(),
                snapshot.asOf(),
                snapshot.filing().filedAt(),
                snapshot.period().fiscalYear(),
                snapshot.period().type().name(),
                snapshot.period().basis().name(),
                snapshot.period().periodEnd(),
                snapshot.statements().stream().map(StatementLineResponse::from).toList(),
                snapshot.metrics().stream().map(MetricResponse::from).toList());
    }

    public record StatementLineResponse(String lineCode, String label, String value, String unit, String scale) {
        static StatementLineResponse from(StatementLine line) {
            return new StatementLineResponse(
                    line.lineCode(), line.label(), line.value().toPlainString(), line.unit(), line.scale());
        }
    }

    public record MetricResponse(String metricCode, String value, String unit, Integer decimals) {
        static MetricResponse from(FundamentalMetric metric) {
            return new MetricResponse(
                    metric.metricCode(), metric.value().toPlainString(), metric.unit(), metric.decimals());
        }
    }
}
