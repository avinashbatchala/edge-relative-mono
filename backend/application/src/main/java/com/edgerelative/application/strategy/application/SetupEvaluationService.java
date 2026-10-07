package com.edgerelative.application.strategy.application;

import com.edgerelative.application.feature.domain.ContextSnapshot;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.feature.service.FeatureSnapshotService;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.strategy.domain.DependencyStatus;
import com.edgerelative.application.strategy.domain.Direction;
import com.edgerelative.application.strategy.domain.StrategyEngine;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput;
import com.edgerelative.application.strategy.domain.StrategyEvaluationResult;
import com.edgerelative.application.strategy.domain.StrategyIdentity;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import com.edgerelative.application.strategy.persistence.SetupObservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one point-in-time setup evaluation per direction: reads feature snapshots through the
 * same engine the dashboard uses, assembles an explicit {@link StrategyEvaluationInput}, evaluates,
 * persists an append-only observation, and publishes a transition event.
 *
 * <p>This service has no path to risk, sizing, or execution. A {@code VALID} result is only a
 * strategy-qualified opportunity. When the strategy is not configured the service does nothing.
 */
@Service
public class SetupEvaluationService {

    private static final String M5 = "M5";
    private static final String D1 = "D1";

    private final FeatureSnapshotService snapshots;
    private final CanonicalInstrumentService canonical;
    private final NseTradingCalendar calendar;
    private final StrategyEngine engine;
    private final StrategyParametersProvider parameters;
    private final SetupObservationRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SetupEvaluationService(
            FeatureSnapshotService snapshots,
            CanonicalInstrumentService canonical,
            NseTradingCalendar calendar,
            StrategyEngine engine,
            StrategyParametersProvider parameters,
            SetupObservationRepository repository,
            ApplicationEventPublisher events,
            Clock clock) {
        this.snapshots = snapshots;
        this.canonical = canonical;
        this.calendar = calendar;
        this.engine = engine;
        this.parameters = parameters;
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    public boolean enabled() {
        return parameters.enabled();
    }

    /**
     * Evaluates both directions for the M5 anchor at or before {@code anchor} (null = latest close).
     * Re-evaluating an already-persisted semantic observation returns the same deterministic result
     * with {@code appended=false} and writes nothing.
     */
    public List<SetupEvaluation> evaluate(long instrumentId, Instant anchor) {
        Optional<StrategyParameters> config = parameters.parameters();
        if (config.isEmpty()) {
            return List.of();
        }
        StrategyParameters params = config.get();
        Instant effectiveAnchor = anchor == null ? clock.instant() : anchor;
        var cache = new ConcurrentHashMap<String, List<AggregatedCandle>>();
        FeatureSnapshot m5 = snapshots.snapshot(instrumentId, M5, effectiveAnchor, false, cache);
        FeatureSnapshot d1 = snapshots.snapshot(instrumentId, D1, effectiveAnchor, false, cache);
        Instant evaluationTime = m5.anchorTimestamp() == null ? effectiveAnchor : m5.anchorTimestamp();

        Optional<Long> strategyVersionId = repository.strategyVersionId();
        if (strategyVersionId.isEmpty()) {
            throw new IllegalStateException("strategy_version seed is missing; apply V014");
        }
        List<AggregatedCandle> subject = snapshots.subjectCandles(instrumentId, M5, evaluationTime, cache);
        if (subject.isEmpty()) {
            return List.of();
        }
        AggregatedCandle completed = subject.get(subject.size() - 1);
        if (!completed.complete()) {
            return List.of();
        }
        long timeframeId = canonical.ensureTimeframe(M5);
        long marketObservationId =
                repository.ensureMarketObservation(instrumentId, timeframeId, completed.closeTime(), null);
        Long sectorId = m5.sector() == null ? null : m5.sector().sectorId();

        List<SetupEvaluation> results = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            StrategyEvaluationInput input = input(instrumentId, m5, d1, subject, direction, evaluationTime);
            StrategyEvaluationResult result = engine.evaluate(input, params, direction);
            boolean appended = repository.append(result, strategyVersionId.get(), marketObservationId, sectorId);
            if (appended) {
                events.publishEvent(new SetupObservationRecorded(result));
            }
            results.add(new SetupEvaluation(result, appended));
        }
        return results;
    }

    public List<SetupObservationView> observations(long instrumentId) {
        return repository.latestForInstrument(instrumentId);
    }

    private StrategyEvaluationInput input(
            long instrumentId,
            FeatureSnapshot m5,
            FeatureSnapshot d1,
            List<AggregatedCandle> subject,
            Direction direction,
            Instant evaluationTime) {
        LocalDate sessionDate = calendar.sessionDate(evaluationTime);
        boolean tradingDay = calendar.isTradingDay(sessionDate);
        AggregatedCandle completed = subject.get(subject.size() - 1);
        StrategyParameters params = parameters.parameters().orElseThrow();

        // Session windows come from the exchange calendar plus configured blackout/cutoff minutes; they
        // are never assumed open. Blackout/cutoff are empty when their configured minutes are 0.
        boolean inSession = calendar.isSessionMinute(evaluationTime);
        boolean openingBlackout = false;
        boolean entryCutoffReached = false;
        if (tradingDay && inSession) {
            Instant open = calendar.sessionOpen(sessionDate);
            Instant close = calendar.sessionClose(sessionDate);
            openingBlackout = params.openingBlackoutMinutes() > 0
                    && evaluationTime.isBefore(open.plusSeconds(60L * params.openingBlackoutMinutes()));
            entryCutoffReached = params.entryCutoffMinutesBeforeClose() > 0
                    && !evaluationTime.isBefore(close.minusSeconds(60L * params.entryCutoffMinutesBeforeClose()));
        }
        boolean entryWindowOpen = inSession && !openingBlackout && !entryCutoffReached;
        StrategyEvaluationInput.SessionContext session = new StrategyEvaluationInput.SessionContext(
                evaluationTime, tradingDay, entryWindowOpen, openingBlackout, entryCutoffReached,
                NseTradingCalendar.VERSION);
        StrategyEvaluationInput.MarketContext market =
                new StrategyEvaluationInput.MarketContext(null, null, null, evaluationTime, false);
        StrategyEvaluationInput.SectorContext sector = sector(m5);
        StrategyEvaluationInput.StockContext stock = new StrategyEvaluationInput.StockContext(
                null,
                number(d1, FeatureKeys.RRS_RAW),
                number(m5, FeatureKeys.RRS_RAW),
                number(m5, FeatureKeys.RRS_FAST),
                number(m5, FeatureKeys.RRS_SLOW),
                number(m5, FeatureKeys.RRS_PERSISTENCE),
                label(m5, FeatureKeys.RRS_TREND_STATE),
                number(m5, FeatureKeys.RVOL_D1),
                number(m5, FeatureKeys.RVOL_INTERVAL),
                number(m5, FeatureKeys.RVOL_CUMULATIVE),
                number(m5, FeatureKeys.RVE),
                number(m5, FeatureKeys.ATR),
                completed.close().doubleValue(),
                null,
                null,
                null,
                null,
                null,
                null,
                StrategyEvaluationInput.StructureContext.empty());
        StrategyEvaluationInput.CompletedCandle candle = new StrategyEvaluationInput.CompletedCandle(
                completed.openTime(),
                completed.closeTime(),
                completed.open(),
                completed.high(),
                completed.low(),
                completed.close(),
                completed.volume());
        return new StrategyEvaluationInput(
                UUID.randomUUID().toString(),
                evaluationTime,
                sessionDate,
                StrategyIdentity.STRATEGY_VERSION,
                parameters.parameters().orElseThrow().parameterSetId(),
                instrumentId,
                evaluationTime.toEpochMilli(),
                "mo:" + instrumentId + ":" + completed.closeTime(),
                session,
                market,
                sector,
                stock,
                candle,
                dependencies(m5, d1),
                StrategyEvaluationInput.PriorSetup.none());
    }

    private static StrategyEvaluationInput.SectorContext sector(FeatureSnapshot m5) {
        ContextSnapshot sector = m5.sector();
        if (sector == null) {
            return new StrategyEvaluationInput.SectorContext(null, null, null, null, Instant.EPOCH, false);
        }
        Double sectorRrs = number(sector.features().get(FeatureKeys.SECTOR_RRS_RAW));
        return new StrategyEvaluationInput.SectorContext(
                sector.sectorId(),
                sector.referenceCode(),
                sectorRrs,
                sector.quality() == null ? null : sector.quality().name(),
                sector.anchorTimestamp(),
                sectorRrs != null);
    }

    /**
     * Declares which inputs producers must supply. Producers for market bias/regime/phase, daily
     * structure, liquidity/spread, technical void, and event risk do not exist yet, so those
     * required dependencies are MISSING and the evaluation fails closed (never VALID).
     */
    private static List<DependencyStatus> dependencies(FeatureSnapshot m5, FeatureSnapshot d1) {
        List<DependencyStatus> dependencies = new ArrayList<>();
        dependencies.add(dep("market.bias", false));
        dependencies.add(dep("market.regime", false));
        dependencies.add(dep("market.phase", false));
        dependencies.add(dep("stock.dailyStructure", false));
        dependencies.add(dep("stock.liquidity", false));
        dependencies.add(dep("stock.technicalVoid", false));
        dependencies.add(dep("stock.eventRisk", false));
        dependencies.add(dep("rrs.d1", number(d1, FeatureKeys.RRS_RAW) != null));
        dependencies.add(dep("rrs.m5", number(m5, FeatureKeys.RRS_RAW) != null));
        dependencies.add(dep("atr.m5", number(m5, FeatureKeys.ATR) != null));
        return dependencies;
    }

    private static DependencyStatus dep(String code, boolean available) {
        return new DependencyStatus(
                code,
                true,
                available ? DependencyStatus.DependencyState.AVAILABLE : DependencyStatus.DependencyState.MISSING);
    }

    private static Double number(FeatureSnapshot snapshot, String key) {
        return snapshot == null ? null : number(snapshot.feature(key));
    }

    private static Double number(FeatureValue value) {
        return value != null && value.availability() == FeatureAvailability.VALID ? value.value() : null;
    }

    private static String label(FeatureSnapshot snapshot, String key) {
        FeatureValue value = snapshot == null ? null : snapshot.feature(key);
        return value != null && value.availability() == FeatureAvailability.VALID ? value.label() : null;
    }
}
