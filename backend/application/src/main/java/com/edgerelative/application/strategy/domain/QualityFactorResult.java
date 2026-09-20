package com.edgerelative.application.strategy.domain;

/**
 * One quality checkbox.
 *
 * @param present whether the favourable condition is observed
 * @param available whether the required inputs existed to judge it (an unavailable optional factor
 *     does not invalidate a setup)
 */
public record QualityFactorResult(QualityFactor factor, boolean present, boolean available, String detail) {

    public static QualityFactorResult of(QualityFactor factor, boolean present, String detail) {
        return new QualityFactorResult(factor, present, true, detail);
    }

    public static QualityFactorResult unavailable(QualityFactor factor, String detail) {
        return new QualityFactorResult(factor, false, false, detail);
    }
}
