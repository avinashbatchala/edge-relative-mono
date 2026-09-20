package com.edgerelative.application.corporateaction.math;

import com.edgerelative.application.corporateaction.domain.CorporateActionFactor;
import com.edgerelative.application.history.AggregatedCandle;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure back-adjustment math (DD-05 §112/§113). Raw candles are never mutated: a bar is adjusted by
 * the product of every factor whose ex-date is strictly after the bar's session date, so the series
 * is continuous up to the present. A bar on the ex-date already trades after the action and is not
 * adjusted.
 */
public final class CorporateActionAdjustment {

    public static final String ADJUSTED_DEFINITION_VERSION = "er-ca-adjusted-v1";
    private static final MathContext PRICE_CONTEXT = new MathContext(24, RoundingMode.HALF_UP);

    private CorporateActionAdjustment() {
    }

    /**
     * An adjusted bar plus the cumulative price factor applied, so the transform is auditable.
     */
    public record AdjustedCandle(AggregatedCandle candle, BigDecimal cumulativePriceFactor) {
    }

    /**
     * Back-adjusts {@code raw} using {@code factors}. Each factor is applied exactly once (a single
     * product per bar), and the raw bars are returned unchanged.
     */
    public static List<AdjustedCandle> backAdjust(List<AggregatedCandle> raw, List<CorporateActionFactor> factors) {
        List<AdjustedCandle> result = new ArrayList<>(raw.size());
        for (AggregatedCandle candle : raw) {
            LocalDate sessionDate = candle.openTime() == null ? null : LocalDate.ofInstant(
                    candle.openTime(), com.edgerelative.application.reference.NseTradingCalendar.EXCHANGE_ZONE);
            BigDecimal priceFactor = BigDecimal.ONE;
            BigDecimal quantityFactor = BigDecimal.ONE;
            for (CorporateActionFactor factor : factors) {
                if (sessionDate != null && factor.exDate() != null && factor.exDate().isAfter(sessionDate)) {
                    priceFactor = priceFactor.multiply(factor.priceFactor(), PRICE_CONTEXT);
                    quantityFactor = quantityFactor.multiply(factor.quantityFactor(), PRICE_CONTEXT);
                }
            }
            result.add(new AdjustedCandle(adjust(candle, priceFactor, quantityFactor), priceFactor));
        }
        return result;
    }

    private static AggregatedCandle adjust(AggregatedCandle candle, BigDecimal priceFactor, BigDecimal quantityFactor) {
        if (BigDecimal.ONE.compareTo(priceFactor) == 0 && BigDecimal.ONE.compareTo(quantityFactor) == 0) {
            return candle;
        }
        long volume = new BigDecimal(candle.volume())
                .multiply(quantityFactor, PRICE_CONTEXT)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return new AggregatedCandle(
                candle.openTime(),
                candle.closeTime(),
                scale(candle.open(), priceFactor),
                scale(candle.high(), priceFactor),
                scale(candle.low(), priceFactor),
                scale(candle.close(), priceFactor),
                volume,
                candle.openInterest(),
                candle.tradeCount(),
                scale(candle.vwap(), priceFactor),
                candle.partial(),
                candle.complete(),
                candle.qualityState(),
                ADJUSTED_DEFINITION_VERSION);
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal factor) {
        return value == null ? null : value.multiply(factor, PRICE_CONTEXT).setScale(8, RoundingMode.HALF_UP);
    }
}
