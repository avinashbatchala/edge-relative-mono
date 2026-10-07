package com.edgerelative.application.backtest.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Run-scoped forensic timeline for one instrument, reconstructed deterministically from the run
 * spec. Every point carries the exact feature values and setup states the run used at that anchor,
 * so an operator can see why an instrument qualified or did not.
 */
public record InstrumentTimeline(
        long instrumentId,
        String symbol,
        List<TimelinePoint> points,
        List<BacktestTrade> trades) {

    public InstrumentTimeline {
        points = points == null ? List.of() : List.copyOf(points);
        trades = trades == null ? List.of() : List.copyOf(trades);
    }

    public record TimelinePoint(
            Instant at,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            Double rrsRaw,
            Double rrsFast,
            Double rrsSlow,
            Double rrsPersistence,
            Double rrsSlope,
            Double rrsAcceleration,
            Double rrsPercentile,
            Double rvolDaily,
            Double rvolInterval,
            Double rvolCumulative,
            Double rve,
            Double atr,
            String marketStructure,
            Double marketEfficiency,
            Double sectorRrs,
            String sectorStructure,
            String marketBias,
            String marketRegime,
            String dailyStructure,
            Double rrsD1,
            String liquidityState,
            String longState,
            String shortState,
            String longPrevious,
            Boolean longValid,
            Boolean longTransitioned,
            List<String> longReasons,
            List<String> shortReasons,
            java.util.Map<String, String> longGates,
            java.util.Map<String, String> shortGates) {

        public TimelinePoint {
            longReasons = longReasons == null ? List.of() : List.copyOf(longReasons);
            shortReasons = shortReasons == null ? List.of() : List.copyOf(shortReasons);
            longGates = longGates == null ? java.util.Map.of() : java.util.Map.copyOf(longGates);
            shortGates = shortGates == null ? java.util.Map.of() : java.util.Map.copyOf(shortGates);
        }
    }
}
