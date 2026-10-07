package com.edgerelative.application.strategy.domain;

import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.MarketContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.PriorSetup;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.SectorContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.SessionContext;
import com.edgerelative.application.strategy.domain.StrategyEvaluationInput.StockContext;
import com.edgerelative.application.strategy.domain.family.FamilyDetection;
import com.edgerelative.application.strategy.domain.family.SetupFamilyRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The deterministic Edge Relative setup engine (DD-02). Pure: it consumes an explicit
 * {@link StrategyEvaluationInput} and returns an explicit {@link StrategyEvaluationResult}. No
 * Spring, database, clock or broker dependency — live, replay and tests call the same code.
 *
 * <p>It fails closed: any missing/stale required dependency is {@code UNAVAILABLE} and cannot yield
 * {@code VALID}. Long and short are separate, explicit evaluations.
 */
public final class StrategyEngine {

    private final SetupFamilyRegistry families;

    public StrategyEngine(SetupFamilyRegistry families) {
        this.families = families;
    }

    public StrategyEvaluationResult evaluate(
            StrategyEvaluationInput input, StrategyParameters parameters, Direction direction) {
        List<HardGateResult> gates = new ArrayList<>();
        HardGateResult data = dataValidity(input);
        HardGateResult session = session(input);
        HardGateResult marketBias = marketBias(input, direction, parameters);
        HardGateResult regime = marketRegime(input);
        HardGateResult daily = dailyStructure(input, direction);
        HardGateResult rrsD1 = rrsD1(input, direction);
        HardGateResult rrsM5 = rrsM5(input, direction, parameters);
        HardGateResult volume = volumeParticipation(input, parameters);
        HardGateResult liquidity = liquidity(input, parameters);
        HardGateResult voidGate = technicalVoid(input, parameters);
        HardGateResult event = eventRisk(input);
        gates.add(data);
        gates.add(session);
        gates.add(marketBias);
        gates.add(regime);
        gates.add(daily);
        gates.add(rrsD1);
        gates.add(rrsM5);
        gates.add(volume);
        gates.add(liquidity);
        gates.add(voidGate);
        gates.add(event);

        SetupFamily family = resolveFamily(input, parameters, direction);
        FamilyDetection detection = family == null
                ? null
                : families.detector(family)
                        .map(detector -> detector.detect(input, parameters, direction))
                        .orElse(null);
        HardGateResult extensionGate = extensionGate(input, parameters, detection);
        gates.add(structureGate(family, detection));
        gates.add(triggerGate(detection));
        gates.add(extensionGate);
        gates.add(invalidationGate(detection, direction));

        List<QualityFactorResult> quality = qualityFactors(input, direction, detection);

        boolean dataOk = data.status().isPassed();
        boolean policyBlock = isPolicyBlock(event, regime, marketBias, session, input, parameters);
        boolean directionEligible = daily.status().isPassed() && rrsD1.status().isPassed()
                && marketBias.status().isPassed();
        boolean watch = directionEligible && liquidity.status().isPassed();
        boolean forming = watch && rrsM5.status().isPassed() && volume.status().isPassed()
                && detection != null && detection.structurePresent();
        boolean nearTrigger = forming && isNearTrigger(input, parameters, detection);
        boolean allGatesPassed = gates.stream().allMatch(gate -> !gate.required() || gate.status().isPassed());
        boolean valid = allGatesPassed && dataOk && watch && forming && detection != null
                && detection.triggerConfirmed() && detection.invalidation() != null;

        PriorSetup prior = input.prior() == null ? PriorSetup.none() : input.prior();
        SetupState previous = prior.state() == null ? SetupState.NONE : prior.state();
        boolean triggerConfirmed = detection != null && detection.triggerConfirmed();
        boolean extensionExceeded = extensionGate.status() == GateStatus.FAILED
                && extensionGate.reasonCode() == ReasonCode.ENTRY_EXTENDED;
        SetupState state = selectState(
                dataOk,
                policyBlock,
                directionEligible,
                watch,
                forming,
                nearTrigger,
                valid,
                triggerConfirmed,
                extensionExceeded,
                previous,
                prior,
                parameters);
        state = reconcileTransition(previous, state, prior);

        List<ReasonCode> reasons = collectReasons(gates, quality, detection, valid, state);
        ReasonCode primary = reasons.isEmpty() ? null : reasons.get(0);
        boolean transitioned = state != previous;
        String explanation = explain(input, direction, family, detection, gates, quality, state, parameters);
        InstanceResolution instance = resolveInstance(input, direction, family, detection, state);

        return new StrategyEvaluationResult(
                input.evaluationId(),
                instance.instanceId(),
                StrategyIdentity.STRATEGY_ID,
                input.strategyVersion(),
                parameters.parameterSetId(),
                parameters.parameterVersion(),
                input.instrumentId(),
                input.featureSnapshotId(),
                input.marketObservationKey(),
                input.evaluationTimestamp(),
                input.tradingDate(),
                direction,
                previous,
                state,
                transitioned,
                instance.initialization(),
                family,
                state == SetupState.VALID,
                value(input.market(), MarketContext::bias),
                value(input.market(), MarketContext::regime),
                value(input.market(), MarketContext::phase),
                input.sector() == null ? null : input.sector().state(),
                value(input.stock(), StockContext::dailyStructure),
                value(input.stock(), StockContext::rrsD1),
                value(input.stock(), StockContext::rrsM5Raw),
                value(input.stock(), StockContext::rrsM5Persistence),
                value(input.stock(), StockContext::rrsTrendState),
                value(input.stock(), StockContext::rvolDaily),
                value(input.stock(), StockContext::rvolInterval),
                value(input.stock(), StockContext::rvolCumulative),
                value(input.stock(), StockContext::rve),
                value(input.stock(), StockContext::liquidityState),
                detection == null ? null : detection.trigger(),
                detection == null ? null : detection.invalidation(),
                null,
                null,
                gates,
                quality,
                reasons,
                primary,
                explanation,
                List.of(
                        "strategy=" + input.strategyVersion(),
                        "parameters=" + parameters.parameterSetId() + "/" + parameters.parameterVersion(),
                        "featureSnapshot=" + input.featureSnapshotId(),
                        "marketObservation=" + input.marketObservationKey()));
    }

    // --- gates ---------------------------------------------------------------------------------

    private HardGateResult dataValidity(StrategyEvaluationInput input) {
        Optional<DependencyStatus> blocking = input.dependencies().stream()
                .filter(DependencyStatus::blocking)
                .findFirst();
        if (blocking.isPresent()) {
            return HardGateResult.failed(
                    GateCode.DATA_VALIDITY,
                    blocking.get().code() + "=" + blocking.get().state(),
                    "AVAILABLE",
                    blocking.get().reasonCode(),
                    blocking.get().code());
        }
        return HardGateResult.passed(GateCode.DATA_VALIDITY, "AVAILABLE", "AVAILABLE", null);
    }

    private HardGateResult session(StrategyEvaluationInput input) {
        SessionContext session = input.session();
        if (session == null || session.calendarVersion() == null) {
            return HardGateResult.unavailable(GateCode.SESSION_ENTRY_ALLOWED, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        if (!session.tradingDay()) {
            return HardGateResult.failed(GateCode.SESSION_ENTRY_ALLOWED, "non-trading-day", "trading-day", ReasonCode.SESSION_ENTRY_CUTOFF, null);
        }
        if (session.openingBlackout()) {
            return HardGateResult.failed(GateCode.SESSION_ENTRY_ALLOWED, "opening-blackout", "open", ReasonCode.OPENING_BLACKOUT, null);
        }
        if (session.entryCutoffReached()) {
            return HardGateResult.failed(GateCode.SESSION_ENTRY_ALLOWED, "cutoff-reached", "before-cutoff", ReasonCode.SESSION_ENTRY_CUTOFF, null);
        }
        if (!session.entryWindowOpen()) {
            return HardGateResult.failed(GateCode.SESSION_ENTRY_ALLOWED, "closed", "open", ReasonCode.SESSION_ENTRY_CUTOFF, null);
        }
        return HardGateResult.passed(GateCode.SESSION_ENTRY_ALLOWED, "open", "open", null);
    }

    private HardGateResult marketBias(StrategyEvaluationInput input, Direction direction, StrategyParameters parameters) {
        MarketContext market = input.market();
        if (market == null || !market.available() || market.bias() == null) {
            return HardGateResult.unavailable(GateCode.MARKET_BIAS_PERMISSION, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        String bias = market.bias();
        if ("NEUTRAL".equals(bias) && parameters.neutralMarketPolicy() == StrategyParameters.NeutralMarketPolicy.BLOCK) {
            return HardGateResult.failed(
                    GateCode.MARKET_BIAS_PERMISSION, bias, "not NEUTRAL (uncalibrated policy)", ReasonCode.NEUTRAL_MARKET_BLOCKED, null);
        }
        boolean opposing = direction.isLong() ? "BEARISH".equals(bias) : "BULLISH".equals(bias);
        if (opposing) {
            return HardGateResult.failed(GateCode.MARKET_BIAS_PERMISSION, bias, "not opposing", ReasonCode.MARKET_OPPOSING, null);
        }
        return HardGateResult.passed(GateCode.MARKET_BIAS_PERMISSION, bias, "not opposing", null);
    }

    private HardGateResult marketRegime(StrategyEvaluationInput input) {
        MarketContext market = input.market();
        if (market == null || !market.available() || market.regime() == null) {
            return HardGateResult.unavailable(GateCode.MARKET_REGIME_PERMISSION, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        if ("DISLOCATED".equals(market.regime())) {
            return HardGateResult.failed(GateCode.MARKET_REGIME_PERMISSION, "DISLOCATED", "not DISLOCATED", ReasonCode.MARKET_DISLOCATED, null);
        }
        if ("TRANSITION".equals(market.regime())) {
            return HardGateResult.failed(
                    GateCode.MARKET_REGIME_PERMISSION, "TRANSITION", "trend/range", ReasonCode.MARKET_TRANSITION_RESTRICTED, null);
        }
        return HardGateResult.passed(GateCode.MARKET_REGIME_PERMISSION, market.regime(), "not prohibited", null);
    }

    private HardGateResult dailyStructure(StrategyEvaluationInput input, Direction direction) {
        String structure = value(input.stock(), StockContext::dailyStructure);
        if (structure == null) {
            return HardGateResult.unavailable(GateCode.DAILY_STRUCTURE_ALIGNED, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        String required = direction.isLong() ? "LONG_ALIGNED" : "SHORT_ALIGNED";
        if (!required.equals(structure)) {
            return HardGateResult.failed(GateCode.DAILY_STRUCTURE_ALIGNED, structure, required, ReasonCode.DAILY_NOT_ALIGNED, null);
        }
        return HardGateResult.passed(GateCode.DAILY_STRUCTURE_ALIGNED, structure, required, null);
    }

    private HardGateResult rrsD1(StrategyEvaluationInput input, Direction direction) {
        Double rrs = value(input.stock(), StockContext::rrsD1);
        if (rrs == null) {
            return HardGateResult.unavailable(GateCode.RRS_D1_DIRECTION, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        boolean ok = direction.isLong() ? rrs > 0.0 : rrs < 0.0;
        return ok
                ? HardGateResult.passed(GateCode.RRS_D1_DIRECTION, rrs.toString(), direction.isLong() ? "> 0" : "< 0", null)
                : HardGateResult.failed(GateCode.RRS_D1_DIRECTION, rrs.toString(), direction.isLong() ? "> 0" : "< 0", ReasonCode.RRS_D1_FAILED, null);
    }

    /**
     * Direction and persistence are independent: the RRS direction must match the trade direction
     * (LONG raw &gt; 0, SHORT raw &lt; 0) and persistence — the share of recent bars agreeing with the
     * RRS direction, a magnitude in [0, 1] — must meet the configured minimum. A persistent bearish
     * stock has negative RRS and persistence near 1.0, so it must qualify for a short on the same
     * "persistence &gt;= minimum" rule as a long.
     */
    private HardGateResult rrsM5(StrategyEvaluationInput input, Direction direction, StrategyParameters parameters) {
        Double raw = value(input.stock(), StockContext::rrsM5Raw);
        Double persistence = value(input.stock(), StockContext::rrsM5Persistence);
        if (raw == null || persistence == null) {
            return HardGateResult.unavailable(GateCode.RRS_M5_PERSISTENCE, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        boolean directionMatches = direction.isLong() ? raw > 0.0 : raw < 0.0;
        if (!directionMatches) {
            String reference = direction.isLong() ? "raw > 0" : "raw < 0";
            return HardGateResult.failed(
                    GateCode.RRS_M5_PERSISTENCE, "raw=" + raw, reference, ReasonCode.RRS_M5_FAILED, null);
        }
        boolean neutralStronger = input.market() != null
                && "NEUTRAL".equals(input.market().bias())
                && parameters.neutralMarketPolicy() == StrategyParameters.NeutralMarketPolicy.REQUIRE_STRONGER;
        double extra = neutralStronger ? parameters.neutralRrsPersistenceExtra() : 0.0;
        double minimum = (direction.isLong()
                        ? parameters.rrsM5PersistenceLongMin()
                        : parameters.rrsM5PersistenceShortMin())
                + extra;
        return persistence > minimum
                ? HardGateResult.passed(
                        GateCode.RRS_M5_PERSISTENCE, persistence.toString(), "raw " + (direction.isLong() ? ">0" : "<0")
                                + " and persistence > " + minimum, null)
                : HardGateResult.failed(
                        GateCode.RRS_M5_PERSISTENCE, persistence.toString(), "> " + minimum, ReasonCode.RRS_M5_FAILED, null);
    }

    private HardGateResult volumeParticipation(StrategyEvaluationInput input, StrategyParameters parameters) {
        Double daily = value(input.stock(), StockContext::rvolDaily);
        Double interval = value(input.stock(), StockContext::rvolInterval);
        Double cumulative = value(input.stock(), StockContext::rvolCumulative);
        if (daily == null && interval == null && cumulative == null) {
            return HardGateResult.unavailable(GateCode.VOLUME_PARTICIPATION, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        boolean elevated = (daily != null && daily >= parameters.minRvolDaily())
                || (interval != null && interval >= parameters.minRvolInterval())
                || (cumulative != null && cumulative >= parameters.minRvolCumulative());
        String actual = "daily=" + daily + ",interval=" + interval + ",cumulative=" + cumulative;
        String reference = ">= [" + parameters.minRvolDaily() + ", " + parameters.minRvolInterval() + ", " + parameters.minRvolCumulative() + "]";
        return elevated
                ? HardGateResult.passed(GateCode.VOLUME_PARTICIPATION, actual, reference, null)
                : HardGateResult.failed(GateCode.VOLUME_PARTICIPATION, actual, reference, ReasonCode.RVOL_FAILED, null);
    }

    private HardGateResult liquidity(StrategyEvaluationInput input, StrategyParameters parameters) {
        String state = value(input.stock(), StockContext::liquidityState);
        Double median = value(input.stock(), StockContext::medianTradedValue);
        if (state == null || median == null) {
            return HardGateResult.unavailable(GateCode.LIQUIDITY, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        boolean ok = !"INVALID".equals(state) && median >= parameters.minLiquidityMedianTradedValue();
        return ok
                ? HardGateResult.passed(GateCode.LIQUIDITY, state + "/" + median, ">=" + parameters.minLiquidityMedianTradedValue(), null)
                : HardGateResult.failed(GateCode.LIQUIDITY, state + "/" + median, ">=" + parameters.minLiquidityMedianTradedValue(), ReasonCode.LIQUIDITY_FAILED, null);
    }

    private HardGateResult technicalVoid(StrategyEvaluationInput input, StrategyParameters parameters) {
        Double voidAtr = value(input.stock(), StockContext::technicalVoidAtr);
        if (voidAtr == null) {
            return HardGateResult.unavailable(GateCode.TECHNICAL_VOID, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        return voidAtr >= parameters.minTechnicalVoidAtr()
                ? HardGateResult.passed(GateCode.TECHNICAL_VOID, voidAtr.toString(), ">=" + parameters.minTechnicalVoidAtr(), null)
                : HardGateResult.failed(GateCode.TECHNICAL_VOID, voidAtr.toString(), ">=" + parameters.minTechnicalVoidAtr(), ReasonCode.NO_TECHNICAL_VOID, null);
    }

    private HardGateResult eventRisk(StrategyEvaluationInput input) {
        StockContext stock = input.stock();
        if (stock == null || stock.eventRiskBlocked() == null) {
            return HardGateResult.unavailable(GateCode.EVENT_RISK, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        return stock.eventRiskBlocked()
                ? HardGateResult.failed(GateCode.EVENT_RISK, "BLOCKED", "not BLOCKED", ReasonCode.EVENT_BLOCKED, null)
                : HardGateResult.passed(GateCode.EVENT_RISK, "CLEAR", "not BLOCKED", null);
    }

    private HardGateResult structureGate(SetupFamily family, FamilyDetection detection) {
        if (family == null) {
            return HardGateResult.failed(GateCode.SETUP_STRUCTURE, "unsupported", "supported family", ReasonCode.SETUP_STRUCTURE_NOT_FOUND, null);
        }
        if (detection != null && detection.structurePresent()) {
            return HardGateResult.passed(GateCode.SETUP_STRUCTURE, family.name(), family.name(), null);
        }
        ReasonCode reason = detection != null && detection.reasons().contains(ReasonCode.SETUP_STRUCTURE_FAILED)
                ? ReasonCode.SETUP_STRUCTURE_FAILED
                : ReasonCode.SETUP_STRUCTURE_NOT_FOUND;
        return HardGateResult.failed(GateCode.SETUP_STRUCTURE, "absent", family.name(), reason, null);
    }

    private HardGateResult triggerGate(FamilyDetection detection) {
        if (detection == null) {
            return HardGateResult.failed(GateCode.TRIGGER_CONFIRMED, "none", "confirmed", ReasonCode.NO_CONFIRMED_TRIGGER, null);
        }
        return detection.triggerConfirmed()
                ? HardGateResult.passed(GateCode.TRIGGER_CONFIRMED, detection.trigger().triggerType(), "confirmed", null)
                : HardGateResult.failed(GateCode.TRIGGER_CONFIRMED, "pending", "confirmed", ReasonCode.NO_CONFIRMED_TRIGGER, null);
    }

    private HardGateResult extensionGate(
            StrategyEvaluationInput input, StrategyParameters parameters, FamilyDetection detection) {
        if (detection == null || detection.trigger() == null || detection.trigger().entryExtensionAtr() == null) {
            return HardGateResult.unavailable(GateCode.ENTRY_EXTENSION, ReasonCode.MISSING_REQUIRED_DEPENDENCY, null);
        }
        double extension = detection.trigger().entryExtensionAtr();
        return extension <= parameters.maxEntryExtensionAtr()
                ? HardGateResult.passed(GateCode.ENTRY_EXTENSION, Double.toString(extension), "<=" + parameters.maxEntryExtensionAtr(), null)
                : HardGateResult.failed(GateCode.ENTRY_EXTENSION, Double.toString(extension), "<=" + parameters.maxEntryExtensionAtr(), ReasonCode.ENTRY_EXTENDED, null);
    }

    private HardGateResult invalidationGate(FamilyDetection detection, Direction direction) {
        if (detection == null || detection.invalidation() == null || detection.triggerLevel() == null) {
            return HardGateResult.failed(GateCode.STRUCTURAL_INVALIDATION, "none", "structural level", ReasonCode.NO_STRUCTURAL_INVALIDATION, null);
        }
        BigDecimal level = detection.invalidation().invalidationLevel();
        if (level == null) {
            return HardGateResult.failed(GateCode.STRUCTURAL_INVALIDATION, "none", "structural level", ReasonCode.NO_STRUCTURAL_INVALIDATION, null);
        }
        boolean directional = direction.isLong()
                ? level.compareTo(detection.triggerLevel()) <= 0
                : level.compareTo(detection.triggerLevel()) >= 0;
        return directional
                ? HardGateResult.passed(GateCode.STRUCTURAL_INVALIDATION, level.toString(), "on the invalidating side", null)
                : HardGateResult.failed(GateCode.STRUCTURAL_INVALIDATION, level.toString(), "on the invalidating side", ReasonCode.NO_STRUCTURAL_INVALIDATION, null);
    }

    // --- state ---------------------------------------------------------------------------------

    private boolean isPolicyBlock(
            HardGateResult event,
            HardGateResult regime,
            HardGateResult marketBias,
            HardGateResult session,
            StrategyEvaluationInput input,
            StrategyParameters parameters) {
        if (event.status() == GateStatus.FAILED) {
            return true;
        }
        if (regime.status() == GateStatus.FAILED) {
            return true;
        }
        if (marketBias.status() == GateStatus.FAILED
                && marketBias.reasonCode() == ReasonCode.NEUTRAL_MARKET_BLOCKED) {
            return true;
        }
        return session.status() == GateStatus.FAILED && session.reasonCode() == ReasonCode.OPENING_BLACKOUT;
    }

    private boolean isNearTrigger(
            StrategyEvaluationInput input, StrategyParameters parameters, FamilyDetection detection) {
        if (detection == null || detection.triggerLevel() == null || detection.triggerConfirmed()) {
            return false;
        }
        Double atr = value(input.stock(), StockContext::atrM5);
        Double last = value(input.stock(), StockContext::lastPrice);
        if (atr == null || atr <= 0 || last == null) {
            return false;
        }
        double distanceAtr = Math.abs(last - detection.triggerLevel().doubleValue()) / atr;
        return distanceAtr <= parameters.nearTriggerDistanceAtr();
    }

    private SetupState selectState(
            boolean dataOk,
            boolean policyBlock,
            boolean directionEligible,
            boolean watch,
            boolean forming,
            boolean nearTrigger,
            boolean valid,
            boolean triggerConfirmed,
            boolean extensionExceeded,
            SetupState previous,
            PriorSetup prior,
            StrategyParameters parameters) {
        if (!dataOk) {
            return previous;
        }
        if (policyBlock) {
            return watch || forming || previous == SetupState.VALID ? SetupState.BLOCKED : SetupState.NONE;
        }
        boolean active = previous == SetupState.WATCH || previous == SetupState.FORMING
                || previous == SetupState.NEAR_TRIGGER || previous == SetupState.VALID;
        boolean expiredByAge = active && prior.barsInState() > parameters.maxBarsInState();
        boolean expiredByTrigger = active
                && prior.triggerTime() != null
                && prior.barsSinceTrigger() > parameters.maxBarsSinceTrigger();
        if (expiredByAge || expiredByTrigger) {
            return SetupState.EXPIRED;
        }
        if (!directionEligible) {
            return previous.isTerminal() ? previous : (previous == SetupState.NONE ? SetupState.NONE : SetupState.INVALIDATED);
        }
        if (valid) {
            return SetupState.VALID;
        }
        if (triggerConfirmed && extensionExceeded) {
            return SetupState.MISSED;
        }
        if (nearTrigger) {
            return SetupState.NEAR_TRIGGER;
        }
        if (forming) {
            return SetupState.FORMING;
        }
        if (watch) {
            return SetupState.WATCH;
        }
        return SetupState.NONE;
    }

    private SetupState reconcileTransition(SetupState previous, SetupState proposed, PriorSetup prior) {
        if (previous == proposed) {
            return proposed;
        }
        if (prior.setupInstanceId() == null && SetupLifecycle.bootstrapAllowed(proposed)) {
            return proposed;
        }
        if (SetupLifecycle.allowed(previous, proposed)) {
            return proposed;
        }
        // The engine evaluates every state condition each bar, so a setup can satisfy several rungs
        // of the DD-02 §74 chain at once (for example FORMING and NEAR_TRIGGER, or a valid trigger
        // on the bar after WATCH). Allow monotonic forward progress along the chain; still reject
        // regressions and jumps out of terminal states.
        if (forwardProgress(previous, proposed)) {
            return proposed;
        }
        return previous;
    }

    private static boolean forwardProgress(SetupState previous, SetupState proposed) {
        return lifecycleRank(previous) >= 0 && lifecycleRank(proposed) > lifecycleRank(previous)
                && !proposed.isTerminal();
    }

    /** Position on the canonical NONE -> WATCH -> FORMING -> NEAR_TRIGGER -> VALID chain. */
    private static int lifecycleRank(SetupState state) {
        return switch (state) {
            case NONE -> 0;
            case WATCH -> 1;
            case FORMING -> 2;
            case NEAR_TRIGGER -> 3;
            case VALID -> 4;
            default -> -1;
        };
    }

    private SetupFamily resolveFamily(
            StrategyEvaluationInput input, StrategyParameters parameters, Direction direction) {
        if (input.prior() != null
                && input.prior().family() != null
                && families.enabled(input.prior().family(), parameters)) {
            return input.prior().family();
        }
        SetupFamily first = null;
        for (SetupFamily family : SetupFamily.values()) {
            if (!families.enabled(family, parameters)) {
                continue;
            }
            if (first == null) {
                first = family;
            }
            if (families.detector(family)
                    .map(detector -> detector.detect(input, parameters, direction).structurePresent())
                    .orElse(false)) {
                return family;
            }
        }
        return first;
    }

    /**
     * A setup instance is created on the first transition from NONE to any active state (cold-start
     * safe), and its identity is derived deterministically from stable setup attributes and the
     * originating canonical observation anchor. It is not created for observations that remain NONE,
     * and it is carried forward unchanged once started. Entering above WATCH with no prior instance
     * is recorded as {@link SetupInitialization#COLD_START_RECONSTRUCTION}; entering at WATCH is a
     * normal {@link SetupInitialization#LIFECYCLE_START}.
     */
    private InstanceResolution resolveInstance(
            StrategyEvaluationInput input,
            Direction direction,
            SetupFamily family,
            FamilyDetection detection,
            SetupState state) {
        if (input.prior() != null && input.prior().setupInstanceId() != null) {
            return new InstanceResolution(input.prior().setupInstanceId(), null);
        }
        if (!startsInstance(state) || family == null) {
            return new InstanceResolution(null, null);
        }
        String structureRef = detection != null && detection.trigger() != null
                ? detection.trigger().triggerType()
                : family.name();
        java.time.Instant origin = input.completedCandle() == null
                ? input.evaluationTimestamp()
                : input.completedCandle().closeTime();
        java.util.UUID instanceId = SetupInstanceId.derive(
                        input.strategyVersion(),
                        input.instrumentId(),
                        direction,
                        family,
                        input.tradingDate(),
                        structureRef,
                        origin)
                .value();
        SetupInitialization initialization = state == SetupState.WATCH
                ? SetupInitialization.LIFECYCLE_START
                : SetupInitialization.COLD_START_RECONSTRUCTION;
        return new InstanceResolution(instanceId, initialization);
    }

    /** True for lifecycle states that constitute a live setup instance (at or beyond WATCH). */
    private static boolean startsInstance(SetupState state) {
        return switch (state) {
            case WATCH, FORMING, NEAR_TRIGGER, VALID -> true;
            default -> false;
        };
    }

    private record InstanceResolution(java.util.UUID instanceId, SetupInitialization initialization) {
    }

    // --- reasons / explainability --------------------------------------------------------------

    private List<ReasonCode> collectReasons(
            List<HardGateResult> gates,
            List<QualityFactorResult> quality,
            FamilyDetection detection,
            boolean valid,
            SetupState state) {
        Set<ReasonCode> codes = new LinkedHashSet<>();
        if (detection != null) {
            codes.addAll(detection.reasons());
        }
        if (state == SetupState.INVALIDATED) {
            codes.add(ReasonCode.SETUP_STRUCTURE_FAILED);
        }
        if (state == SetupState.EXPIRED) {
            codes.add(ReasonCode.TRIGGER_EXPIRED);
        }
        if (state == SetupState.MISSED) {
            codes.add(ReasonCode.ENTRY_EXTENDED);
        }
        for (HardGateResult gate : gates) {
            if (gate.required() && !gate.status().isPassed() && gate.reasonCode() != null) {
                codes.add(gate.reasonCode());
            }
            if (gate.gateCode() == GateCode.DATA_VALIDITY && gate.status() == GateStatus.FAILED) {
                // Always include the canonical DATA_INVALID code alongside the specific dependency code.
                codes.add(ReasonCode.DATA_INVALID);
            }
        }
        List<ReasonCode> ordered = new ArrayList<>(codes);
        ordered.sort(Comparator.comparingInt(ReasonCode::priority));
        return List.copyOf(ordered);
    }

    private List<QualityFactorResult> qualityFactors(
            StrategyEvaluationInput input, Direction direction, FamilyDetection detection) {
        List<QualityFactorResult> factors = new ArrayList<>();
        SectorContext sector = input.sector();
        StockContext stock = input.stock();
        if (sector != null && sector.available() && sector.sectorRrs() != null) {
            boolean aligned = direction.isLong() ? sector.sectorRrs() > 0 : sector.sectorRrs() < 0;
            factors.add(QualityFactorResult.of(QualityFactor.SECTOR_ALIGNMENT, aligned, "sector rrs=" + sector.sectorRrs()));
        } else {
            factors.add(QualityFactorResult.unavailable(QualityFactor.SECTOR_ALIGNMENT, "sector context unavailable"));
        }
        if (stock != null && stock.rrsTrendState() != null) {
            boolean rising = direction.isLong()
                    ? "POSITIVE_RISING".equals(stock.rrsTrendState())
                    : "NEGATIVE_FALLING".equals(stock.rrsTrendState());
            factors.add(QualityFactorResult.of(QualityFactor.RISING_RRS, rising, stock.rrsTrendState()));
        }
        factors.add(QualityFactorResult.unavailable(QualityFactor.DAILY_MA_STACK, "daily moving-average stack not produced"));
        factors.add(QualityFactorResult.unavailable(QualityFactor.HEIKIN_ASHI_CONFIRMATION, "Heikin-Ashi not produced"));
        factors.add(detection != null && detection.triggerConfirmed()
                ? QualityFactorResult.of(QualityFactor.TRIGGER_QUALITY, true, detection.trigger().triggerType())
                : QualityFactorResult.unavailable(QualityFactor.TRIGGER_QUALITY, "no confirmed trigger"));
        return factors;
    }

    private String explain(
            StrategyEvaluationInput input,
            Direction direction,
            SetupFamily family,
            FamilyDetection detection,
            List<HardGateResult> gates,
            List<QualityFactorResult> quality,
            SetupState state,
            StrategyParameters parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append(direction).append(' ').append(state);
        if (family != null) {
            builder.append(" [").append(family.name()).append(']');
        }
        builder.append(System.lineSeparator());
        for (HardGateResult gate : gates) {
            builder.append(gate.gateCode()).append(": ").append(gate.status());
            if (!gate.status().isPassed() && gate.reasonCode() != null) {
                builder.append(" (").append(gate.reasonCode()).append(')');
            }
            builder.append(System.lineSeparator());
        }
        long present = quality.stream().filter(QualityFactorResult::present).count();
        builder.append("qualityCheckboxCount=").append(present).append('/').append(quality.size());
        return builder.toString();
    }

    // --- helpers -------------------------------------------------------------------------------

    private static <C, V> V value(C context, java.util.function.Function<C, V> getter) {
        return context == null ? null : getter.apply(context);
    }
}
