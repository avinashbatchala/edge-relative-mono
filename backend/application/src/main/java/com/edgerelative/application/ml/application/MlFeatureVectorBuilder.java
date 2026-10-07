package com.edgerelative.application.ml.application;

import com.edgerelative.application.feature.domain.ContextSnapshot;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.ml.domain.MlFeatureVector;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.SetupTrigger;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import com.edgerelative.application.strategy.domain.StructuralInvalidation;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Builds the ML feature vector for one VALID anchor from the exact production {@link FeatureSnapshot}
 * plus derived context. Every numeric feature is ATR/price-normalized where appropriate; categorical
 * inputs are one-hot encoded so the model has only numeric splits. A missing feature is omitted (the
 * evaluator applies the model's default direction) and never coerced to zero.
 *
 * <p>Session high/low and time-of-day are computed strictly from data at or before the anchor, so no
 * future information leaks into a decision.
 */
@Component
public class MlFeatureVectorBuilder {

    public MlFeatureVector build(
            FeatureSnapshot snapshot,
            StrategyEvaluationResult chosen,
            AggregatedCandle bar,
            Instant anchor,
            List<AggregatedCandle> sessionHistory,
            NseTradingCalendar calendar) {
        MlFeatureVector.Builder b = MlFeatureVector.builder();

        Double atr = numeric(snapshot, FeatureKeys.ATR);
        Double close = bar.close() == null ? null : bar.close().doubleValue();

        b.numeric(FeatureKeys.ATR, atr)
                .numeric("DERIVED_ATR_PERCENT", ratio(atr, close))
                .numeric(FeatureKeys.RRS_RAW, numeric(snapshot, FeatureKeys.RRS_RAW))
                .numeric(FeatureKeys.RRS_FAST, numeric(snapshot, FeatureKeys.RRS_FAST))
                .numeric(FeatureKeys.RRS_SLOW, numeric(snapshot, FeatureKeys.RRS_SLOW))
                .numeric(FeatureKeys.RRS_PERSISTENCE, numeric(snapshot, FeatureKeys.RRS_PERSISTENCE))
                .numeric(FeatureKeys.RRS_SLOPE, numeric(snapshot, FeatureKeys.RRS_SLOPE))
                .numeric(FeatureKeys.RRS_ACCELERATION, numeric(snapshot, FeatureKeys.RRS_ACCELERATION))
                .numeric(FeatureKeys.RRS_PERCENTILE, numeric(snapshot, FeatureKeys.RRS_PERCENTILE))
                .numeric(FeatureKeys.RRS_VS_SECTOR_RAW, numeric(snapshot, FeatureKeys.RRS_VS_SECTOR_RAW))
                .numeric(FeatureKeys.RVOL_D1, numeric(snapshot, FeatureKeys.RVOL_D1))
                .numeric(FeatureKeys.RVOL_INTERVAL, numeric(snapshot, FeatureKeys.RVOL_INTERVAL))
                .numeric(FeatureKeys.RVOL_CUMULATIVE, numeric(snapshot, FeatureKeys.RVOL_CUMULATIVE))
                .numeric(FeatureKeys.RVE, numeric(snapshot, FeatureKeys.RVE))
                .numeric(FeatureKeys.DIRECTIONAL_VOLUME_LONG, numeric(snapshot, FeatureKeys.DIRECTIONAL_VOLUME_LONG))
                .numeric(FeatureKeys.DIRECTIONAL_VOLUME_SHORT, numeric(snapshot, FeatureKeys.DIRECTIONAL_VOLUME_SHORT));

        ContextSnapshot market = snapshot.market();
        ContextSnapshot sector = snapshot.sector();
        b.numeric(FeatureKeys.MARKET_ATR, contextNumeric(market, FeatureKeys.MARKET_ATR))
                .numeric(FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY, contextNumeric(market, FeatureKeys.MARKET_DIRECTIONAL_EFFICIENCY))
                .numeric(FeatureKeys.SECTOR_RRS_RAW, contextNumeric(sector, FeatureKeys.SECTOR_RRS_RAW))
                .numeric(FeatureKeys.SECTOR_DIRECTIONAL_EFFICIENCY, contextNumeric(sector, FeatureKeys.SECTOR_DIRECTIONAL_EFFICIENCY));

        applyTradeGeometry(b, snapshot, chosen, bar, atr);
        applySessionContext(b, anchor, sessionHistory, calendar, close);
        applyAlignment(b, snapshot, chosen);
        applyCategoricals(b, snapshot, market, sector, chosen);
        return b.build();
    }

    private void applyTradeGeometry(
            MlFeatureVector.Builder b, FeatureSnapshot snapshot, StrategyEvaluationResult chosen,
            AggregatedCandle bar, Double atr) {
        SetupTrigger trigger = chosen == null ? null : chosen.trigger();
        StructuralInvalidation invalidation = chosen == null ? null : chosen.invalidation();
        Double entry = trigger != null && trigger.triggerLevel() != null
                ? trigger.triggerLevel().doubleValue()
                : (bar.close() == null ? null : bar.close().doubleValue());
        Double stop = invalidation != null && invalidation.invalidationLevel() != null
                ? invalidation.invalidationLevel().doubleValue()
                : null;
        Double rewardRisk = chosen == null ? null : chosen.structuralRR();
        Double stopDistanceAtr = atr != null && atr != 0.0 && entry != null && stop != null
                ? Math.abs(entry - stop) / atr
                : null;
        b.numeric("DERIVED_distanceEntryToStopAtr", stopDistanceAtr)
                .numeric("DERIVED_plannedRewardRisk", rewardRisk)
                .numeric("DERIVED_distanceEntryToTargetAtr",
                        stopDistanceAtr != null && rewardRisk != null ? stopDistanceAtr * rewardRisk : null)
                .numeric("DERIVED_entryExtensionAtr",
                        trigger == null ? null : trigger.entryExtensionAtr());
    }

    private void applySessionContext(
            MlFeatureVector.Builder b, Instant anchor, List<AggregatedCandle> sessionHistory,
            NseTradingCalendar calendar, Double close) {
        LocalDate date = calendar.sessionDate(anchor);
        b.numeric("DERIVED_dayOfWeek", (double) date.getDayOfWeek().getValue());
        if (!calendar.isTradingDay(date)) {
            return;
        }
        Instant open = calendar.sessionOpen(date);
        Instant closeAt = calendar.sessionClose(date);
        b.numeric("DERIVED_minutesSinceOpen", (double) Duration.between(open, anchor).toMinutes())
                .numeric("DERIVED_minutesToClose", (double) Duration.between(anchor, closeAt).toMinutes());
        if (sessionHistory == null || sessionHistory.isEmpty()) {
            return;
        }
        Double high = null;
        Double low = null;
        for (AggregatedCandle candle : sessionHistory) {
            if (!anchor.isBefore(candle.closeTime() == null ? candle.openTime() : candle.closeTime())) {
                double h = candle.high() == null ? Double.NaN : candle.high().doubleValue();
                double l = candle.low() == null ? Double.NaN : candle.low().doubleValue();
                high = high == null ? h : Math.max(high, h);
                low = low == null ? l : Math.min(low, l);
            }
        }
        if (high != null && low != null && high > low && close != null) {
            b.numeric("DERIVED_priceVsSessionHighPct", (close - high) / high * 100.0)
                    .numeric("DERIVED_priceVsSessionLowPct", (close - low) / low * 100.0)
                    .numeric("DERIVED_rangePositionInSession", (close - low) / (high - low));
        }
    }

    private void applyAlignment(MlFeatureVector.Builder b, FeatureSnapshot snapshot, StrategyEvaluationResult chosen) {
        if (chosen == null || chosen.direction() == null) {
            return;
        }
        boolean longSide = chosen.direction() == Direction.LONG;
        int align = 0;
        align += agrees(numeric(snapshot, FeatureKeys.RRS_RAW), longSide) ? 1 : 0;
        align += agrees(numeric(snapshot, FeatureKeys.RRS_VS_SECTOR_RAW), longSide) ? 1 : 0;
        align += agrees(contextNumeric(snapshot.sector(), FeatureKeys.SECTOR_RRS_RAW), longSide) ? 1 : 0;
        Double rrsD1 = chosen.rrsD1();
        align += rrsD1 != null && (rrsD1 > 0) == longSide ? 1 : 0;
        b.numeric("DERIVED_alignmentCount", (double) align);
    }

    private void applyCategoricals(
            MlFeatureVector.Builder b, FeatureSnapshot snapshot, ContextSnapshot market,
            ContextSnapshot sector, StrategyEvaluationResult chosen) {
        if (chosen != null && chosen.direction() != null) {
            b.categorical("direction", chosen.direction().name());
        }
        if (chosen != null && chosen.setupFamily() != null) {
            b.categorical("setupFamily", chosen.setupFamily().name());
        }
        b.categorical("rrsTrendState", label(snapshot, FeatureKeys.RRS_TREND_STATE))
                .categorical("marketStructure", contextLabel(market, FeatureKeys.MARKET_PRICE_STRUCTURE))
                .categorical("sectorStructure", contextLabel(sector, FeatureKeys.SECTOR_PRICE_STRUCTURE));
        if (snapshot.benchmark() != null) {
            b.categorical("sectorCode", snapshot.benchmark().sectorCode());
        }
    }

    private static boolean agrees(Double value, boolean longSide) {
        return value != null && (value > 0) == longSide;
    }

    private static Double ratio(Double numerator, Double denominator) {
        return numerator != null && denominator != null && denominator != 0.0
                ? numerator / denominator
                : null;
    }

    private static Double numeric(FeatureSnapshot snapshot, String key) {
        return number(snapshot == null ? null : snapshot.feature(key));
    }

    private static Double contextNumeric(ContextSnapshot context, String key) {
        return number(context == null ? null : context.features().get(key));
    }

    private static String label(FeatureSnapshot snapshot, String key) {
        FeatureValue value = snapshot == null ? null : snapshot.feature(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }

    private static String contextLabel(ContextSnapshot context, String key) {
        FeatureValue value = context == null ? null : context.features().get(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }

    private static Double number(FeatureValue value) {
        return value != null && value.availability() == FeatureAvailability.VALID ? value.value() : null;
    }
}
