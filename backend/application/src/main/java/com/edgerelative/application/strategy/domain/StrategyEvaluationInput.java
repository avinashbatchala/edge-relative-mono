package com.edgerelative.application.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Explicit point-in-time input for one strategy evaluation. Null fields mean "not produced"; the
 * engine treats them as unavailable and fails closed rather than substituting a value. Live, replay
 * and tests all construct this same object.
 */
public record StrategyEvaluationInput(
        String evaluationId,
        Instant evaluationTimestamp,
        LocalDate tradingDate,
        String strategyVersion,
        String parameterSetId,
        long instrumentId,
        long featureSnapshotId,
        String marketObservationKey,
        SessionContext session,
        MarketContext market,
        SectorContext sector,
        StockContext stock,
        CompletedCandle completedCandle,
        List<DependencyStatus> dependencies,
        PriorSetup prior) {

    public StrategyEvaluationInput {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public record SessionContext(
            Instant exchangeTimestamp,
            boolean tradingDay,
            boolean entryWindowOpen,
            boolean openingBlackout,
            boolean entryCutoffReached,
            String calendarVersion) {
    }

    public record MarketContext(
            String bias,
            String regime,
            String phase,
            Instant timestamp,
            boolean available) {
    }

    public record SectorContext(
            Long sectorId,
            String sectorCode,
            Double sectorRrs,
            String state,
            Instant timestamp,
            boolean available) {
    }

    public record StockContext(
            String dailyStructure,
            Double rrsD1,
            Double rrsM5Raw,
            Double rrsM5Fast,
            Double rrsM5Slow,
            Double rrsM5Persistence,
            String rrsTrendState,
            Double rvolDaily,
            Double rvolInterval,
            Double rvolCumulative,
            Double rve,
            Double atrM5,
            Double lastPrice,
            Double tickSize,
            String liquidityState,
            Double medianTradedValue,
            Double spreadBps,
            Double technicalVoidAtr,
            Boolean eventRiskBlocked,
            StructureContext structure) {
    }

    /** Explicit family structure known at the evaluation timestamp. */
    public record StructureContext(
            boolean compressionPresent,
            BigDecimal compressionHigh,
            BigDecimal compressionLow,
            Double compressionEfficiency,
            Double compressionOverlap,
            boolean horizontalLevelPresent,
            BigDecimal horizontalLevel,
            boolean ema38Present,
            BigDecimal ema3,
            BigDecimal ema8,
            BigDecimal ema3Previous,
            BigDecimal ema8Previous) {

        public static StructureContext empty() {
            return new StructureContext(false, null, null, null, null, false, null, false, null, null, null, null);
        }
    }

    /** The completed M5 candle used for confirmation. In-progress candles never confirm a trigger. */
    public record CompletedCandle(
            Instant openTime,
            Instant closeTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume) {
    }

    public record PriorSetup(
            UUID setupInstanceId,
            SetupState state,
            SetupFamily family,
            Direction direction,
            int barsInState,
            int barsSinceTrigger,
            Instant triggerTime,
            BigDecimal triggerLevel,
            BigDecimal invalidationLevel) {

        public static PriorSetup none() {
            return new PriorSetup(null, SetupState.NONE, null, null, 0, 0, null, null, null);
        }
    }
}
