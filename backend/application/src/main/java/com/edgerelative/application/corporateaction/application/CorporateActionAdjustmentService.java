package com.edgerelative.application.corporateaction.application;

import com.edgerelative.application.corporateaction.CorporateActionException;
import com.edgerelative.application.corporateaction.domain.CorporateActionFactor;
import com.edgerelative.application.corporateaction.math.CorporateActionAdjustment;
import com.edgerelative.application.corporateaction.math.CorporateActionAdjustment.AdjustedCandle;
import com.edgerelative.application.corporateaction.persistence.CorporateActionFactorRepository;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.history.query.HistoricalDataReader;
import com.edgerelative.application.reference.NseTradingCalendar;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Builds an explicitly versioned, back-adjusted analytical series from raw canonical candles
 * (DD-05 §112/§113). Raw storage is never modified; every call re-derives from canonical M1.
 *
 * <p>Fails closed: if any action affecting the window is unsupported or lacks a factor available at
 * the requested as-of time, no adjusted series is returned (DD-05 §43/§260) rather than silently
 * crossing the action.
 */
@Service
public class CorporateActionAdjustmentService {

    public static final String ADJUSTMENT_SPLIT_BONUS = "SPLIT_BONUS";

    private final CorporateActionFactorRepository factors;
    private final HistoricalDataReader reader;
    private final Clock clock;

    public CorporateActionAdjustmentService(
            CorporateActionFactorRepository factors, HistoricalDataReader reader, Clock clock) {
        this.factors = factors;
        this.reader = reader;
        this.clock = clock;
    }

    public List<AdjustedCandle> adjustedCandles(
            long instrumentId, String timeframe, Instant from, Instant to, int limit, Instant asOf) {
        Instant effectiveAsOf = asOf == null ? clock.instant() : asOf;
        LocalDate fromDate = LocalDate.ofInstant(from, NseTradingCalendar.EXCHANGE_ZONE);
        List<String> unsupported = factors.unsupportedActions(instrumentId, fromDate);
        if (!unsupported.isEmpty()) {
            throw new CorporateActionException(
                    CorporateActionException.UNSUPPORTED,
                    "Unsupported corporate action(s) affect this window: " + String.join(", ", unsupported)
                            + "; adjusted series is unavailable (split/bonus only)");
        }
        List<AggregatedCandle> raw = reader.candles(instrumentId, timeframe, from, to, limit);
        List<CorporateActionFactor> known = factors.listFactors(instrumentId, effectiveAsOf);
        return CorporateActionAdjustment.backAdjust(raw, known);
    }
}
