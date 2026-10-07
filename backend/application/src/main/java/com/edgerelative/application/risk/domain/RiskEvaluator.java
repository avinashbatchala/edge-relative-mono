package com.edgerelative.application.risk.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Pure deterministic risk evaluator (DD-03). No database, network, clock, or Spring dependency:
 * callers pass the candidate, an immutable {@link RiskContext}, and a versioned {@link RiskPolicy}.
 * Identical inputs always produce an identical proposal.
 *
 * <p>Resolution of DD-03 ambiguities is deliberately conservative and documented in
 * {@code docs/design-docs/dev/ADR-003-risk-capacity-accounting.md}:
 * <ul>
 *   <li>the risk-state multiplier is applied exactly once, to the post-min() budget;</li>
 *   <li>session capacity consumes realized loss + reserved risk; portfolio capacity consumes open
 *       risk + reserved risk; strategy/symbol/sector consume realized + open + reserved;</li>
 *   <li>correlation acts only as a reducer on the budget (no second quantity cap).</li>
 * </ul>
 */
public final class RiskEvaluator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public RiskDecisionProposal evaluate(RiskCandidate candidate, RiskContext context, RiskPolicy policy) {
        Eval e = new Eval();
        if (policy == null) {
            e.reasons.add(RiskReasonCode.MISSING_POLICY);
            e.fatal = true;
            return reject(candidate, context, null, e, LossProfile.invalid(null, null, RiskReasonCode.MISSING_POLICY));
        }
        if (!policy.appliesTo(candidate.mode())) {
            e.fail("policy.applicability", ConstraintKind.RATIO, ZERO, ZERO, null,
                    RiskReasonCode.MISSING_POLICY, true);
        }
        evaluatePreconditions(candidate, context, policy, e);

        LossProfile loss = lossProfile(candidate, policy);
        if (!loss.valid()) {
            e.fail("stop.distance", ConstraintKind.CURRENCY, null, ZERO, null,
                    loss.invalidReason() == null ? RiskReasonCode.INVALID_STOP_DISTANCE : loss.invalidReason(), true);
        }
        if (e.fatal || !loss.valid() || !budgetContextAvailable(context, policy, e)) {
            return reject(candidate, context, policy, e, loss);
        }

        BigDecimal rre = context.riskReferenceEquity();
        BudgetResult budget = computeBudget(candidate, context, policy, e, rre);
        if (budget.budget().signum() <= 0 && !e.fatal) {
            e.reasons.add(RiskReasonCode.INSUFFICIENT_RISK_CAPACITY);
        }

        long riskSized = RiskMath.floorQuantity(budget.budget(), loss.effectiveLossPerUnit());
        QuantityPlan plan = quantities(candidate, context, policy, e, rre, loss, riskSized);

        long finalQuantity = Math.min(riskSized, plan.maxQuantity());
        if (candidate.requestedQuantitySupplied()) {
            finalQuantity = Math.min(finalQuantity, candidate.requestedQuantity());
        }
        finalQuantity = Math.max(RiskMath.roundDownToIncrement(finalQuantity, candidate.quantityIncrement()), 0);

        boolean fatalAfterCaps = e.fatal;
        RiskDecisionType decision;
        if (e.halt) {
            decision = RiskDecisionType.HALT_REQUIRED;
        } else if (fatalAfterCaps || finalQuantity < 1) {
            decision = RiskDecisionType.REJECT;
            if (!fatalAfterCaps) {
                RiskReasonCode binding = plan.firstBindingReason();
                e.reasons.add(binding == null ? RiskReasonCode.INSUFFICIENT_RISK_CAPACITY : binding);
            }
        } else if (candidate.requestedQuantitySupplied() && finalQuantity < candidate.requestedQuantity()) {
            decision = RiskDecisionType.REDUCE;
        } else {
            decision = RiskDecisionType.APPROVE;
        }

        if (decision.authorizesNewRisk() && finalQuantity < riskSized) {
            e.reasons.add(RiskReasonCode.RISK_REDUCED_CONSTRAINT_LIMIT);
        }
        long approvedFinal = finalQuantity;
        List<QuantityCap> caps = plan.caps().stream()
                .map(cap -> markBinding(cap, approvedFinal, riskSized))
                .toList();
        BigDecimal approvedNotional = decision.authorizesNewRisk()
                ? RiskMath.money(candidate.proposedEntryPrice().multiply(BigDecimal.valueOf(finalQuantity)))
                : ZERO;
        BigDecimal nominalRisk = decision.authorizesNewRisk() ? RiskMath.multiply(loss.plannedLossPerUnit(), BigDecimal.valueOf(finalQuantity)) : ZERO;
        BigDecimal execRisk = decision.authorizesNewRisk() ? RiskMath.multiply(loss.effectiveLossPerUnit(), BigDecimal.valueOf(finalQuantity)) : ZERO;
        BigDecimal stressRisk = decision.authorizesNewRisk() && loss.stressLossPerUnit() != null
                ? RiskMath.multiply(loss.stressLossPerUnit(), BigDecimal.valueOf(finalQuantity))
                : ZERO;

        return build(
                candidate, context, policy, e, loss, decision, budget,
                riskSized, finalQuantity, approvedNotional, nominalRisk, execRisk, stressRisk,
                approvedNotional, caps, budget.preCapacity());
    }

    // --- preconditions --------------------------------------------------------------------------

    private void evaluatePreconditions(RiskCandidate c, RiskContext ctx, RiskPolicy p, Eval e) {
        e.gate("context.account", ConstraintKind.RATIO, ctx.accountAvailable(), RiskReasonCode.DATA_QUALITY_BLOCKED);
        e.gate("context.portfolio", ConstraintKind.RATIO, ctx.portfolioAvailable(), RiskReasonCode.DATA_QUALITY_BLOCKED);
        e.gate("context.broker", ConstraintKind.RATIO, ctx.brokerAvailable(), RiskReasonCode.BROKER_HEALTH_BLOCKED);
        e.gate("broker.health", ConstraintKind.RATIO, trusted(ctx.brokerHealth(), "HEALTHY", "OK", "TRUSTED"),
                RiskReasonCode.BROKER_HEALTH_BLOCKED);
        e.gate("reconciliation.state", ConstraintKind.RATIO,
                trusted(ctx.reconciliationState(), "MATCHED", "RECONCILED", "OK"),
                RiskReasonCode.RECONCILIATION_MISMATCH);
        e.gate("data.health", ConstraintKind.RATIO, trusted(ctx.dataHealth(), "HEALTHY", "VALID", "OK"),
                RiskReasonCode.DATA_QUALITY_BLOCKED);
        e.gate("order.state", ConstraintKind.RATIO, ctx.orderStateKnown(), RiskReasonCode.ORDER_STATE_UNKNOWN);

        e.gate("setup.validity", ConstraintKind.RATIO,
                c.setupValid() && "VALID".equalsIgnoreCase(c.setupStatus()), RiskReasonCode.INVALID_SETUP);
        e.gate("candidate.invalidation", ConstraintKind.RATIO,
                c.structuralInvalidation() != null && c.structuralInvalidation().signum() > 0,
                RiskReasonCode.INVALID_SETUP);

        RiskContext.SessionWindow session = ctx.session();
        if (session == null) {
            e.unavailable("session.entry", ConstraintKind.RATIO, RiskReasonCode.MISSING_REQUIRED_INPUT);
        } else {
            e.gate("session.tradingDay", ConstraintKind.RATIO, session.tradingDay(), RiskReasonCode.SESSION_FLATTEN_WINDOW);
            e.gate("session.entryWindow", ConstraintKind.RATIO, session.entryWindowOpen(), RiskReasonCode.SESSION_FLATTEN_WINDOW);
            e.gate("session.blackout", ConstraintKind.RATIO, !session.openingBlackout(), RiskReasonCode.SESSION_FLATTEN_WINDOW);
            e.gate("session.cutoff", ConstraintKind.RATIO, !session.entryCutoffReached(), RiskReasonCode.SESSION_FLATTEN_WINDOW);
            e.gate("session.flattenWindow", ConstraintKind.RATIO, !session.flattenWindow(), RiskReasonCode.SESSION_FLATTEN_WINDOW);
        }

        e.gate("market.regime", ConstraintKind.RATIO, c.marketRegimeKnown(), RiskReasonCode.MISSING_REQUIRED_INPUT);
        e.gate("event.risk", ConstraintKind.RATIO, c.eventRiskKnown() && !c.eventRiskBlocked(),
                RiskReasonCode.EVENT_RISK_BLOCKED);

        RiskState state = ctx.riskState();
        if (state == null) {
            e.unavailable("risk.state", ConstraintKind.RATIO, RiskReasonCode.MISSING_REQUIRED_INPUT);
        } else if (state == RiskState.HALTED) {
            e.halt = true;
            e.fail("risk.state", ConstraintKind.RATIO, ZERO, ZERO, null, RiskReasonCode.RISK_STATE_BLOCKED, true);
            e.reasons.add(RiskReasonCode.HALT_REQUIRED);
        } else if (!state.permitsNewRisk()) {
            e.fail("risk.state", ConstraintKind.RATIO, ZERO, ZERO, null, RiskReasonCode.RISK_STATE_BLOCKED, true);
        }

        drawdown(ctx, p, e);
        if (ctx.consecutiveLosses() > 0 && p.drawdown() != null && p.drawdown().maxConsecutiveLosses() != null
                && ctx.consecutiveLosses() >= p.drawdown().maxConsecutiveLosses()) {
            e.fail("loss.streak", ConstraintKind.COUNT, BigDecimal.valueOf(ctx.consecutiveLosses()),
                    BigDecimal.valueOf(p.drawdown().maxConsecutiveLosses()), null,
                    RiskReasonCode.CONSECUTIVE_LOSS_LIMIT, true);
        }

        RiskContext.SymbolExposure existing = ctx.symbol(c.instrumentId());
        if (existing != null && existing.signedQuantity() != 0) {
            e.fail("exposure.addToExisting", ConstraintKind.QUANTITY,
                    BigDecimal.valueOf(existing.signedQuantity()), ZERO, null,
                    RiskReasonCode.PROHIBITED_ADDITION, true);
        }

        if (p.liquidity() != null) {
            if (p.liquidity().spreadMandatory() && c.spreadBps() == null) {
                e.unavailable("liquidity.spread", ConstraintKind.RATIO, RiskReasonCode.SPREAD_LIMIT);
            } else if (c.spreadBps() != null && p.liquidity().maxSpreadBps() != null
                    && BigDecimal.valueOf(c.spreadBps()).compareTo(p.liquidity().maxSpreadBps()) > 0) {
                e.fail("liquidity.spread", ConstraintKind.RATIO, BigDecimal.valueOf(c.spreadBps()),
                        p.liquidity().maxSpreadBps(), null, RiskReasonCode.SPREAD_LIMIT, true);
            }
            if (p.liquidity().liquidityMandatory() && c.expectedExecutableVolume() == null) {
                e.unavailable("liquidity.volume", ConstraintKind.RATIO, RiskReasonCode.LIQUIDITY_LIMIT);
            }
            if (p.liquidity().brokerCapMandatory() && c.brokerMaxQuantity() == null) {
                e.unavailable("broker.quantity", ConstraintKind.QUANTITY, RiskReasonCode.BROKER_QUANTITY_LIMIT);
            }
        }

        if (p.sector() != null && p.sector().sectorMappingMandatory() && c.sectorId() == null) {
            e.unavailable("sector.mapping", ConstraintKind.RATIO, RiskReasonCode.SECTOR_CONCENTRATION_LIMIT);
        }
    }

    private void drawdown(RiskContext ctx, RiskPolicy p, Eval e) {
        RiskPolicy.DrawdownLimits d = p.drawdown();
        BigDecimal rre = ctx.riskReferenceEquity();
        if (d == null || rre == null) {
            e.unavailable("drawdown.policy", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
            return;
        }
        threshold(e, "drawdown.daily.stopNew", ctx.sessionDrawdown(), fraction(rre, d.dailyStopNewFraction()),
                RiskReasonCode.DAILY_DRAWDOWN_LIMIT);
        threshold(e, "drawdown.weekly", ctx.weeklyDrawdown(), fraction(rre, d.weeklyLimitFraction()),
                RiskReasonCode.WEEKLY_DRAWDOWN_LIMIT);
        threshold(e, "drawdown.monthly", ctx.monthlyDrawdown(), fraction(rre, d.monthlyLimitFraction()),
                RiskReasonCode.MONTHLY_DRAWDOWN_LIMIT);
        threshold(e, "drawdown.account", ctx.accountDrawdown(), fraction(rre, d.accountLimitFraction()),
                RiskReasonCode.ACCOUNT_DRAWDOWN_LIMIT);
    }

    private static void threshold(Eval e, String name, BigDecimal actual, BigDecimal limit, RiskReasonCode code) {
        if (actual == null || limit == null) {
            return;
        }
        if (actual.compareTo(limit) > 0) {
            e.fail(name, ConstraintKind.CURRENCY, actual, limit, ZERO, code, true);
        } else {
            e.pass(name, ConstraintKind.CURRENCY, actual, limit, limit.subtract(actual));
        }
    }

    private static BigDecimal fraction(BigDecimal base, BigDecimal fraction) {
        return fraction == null ? null : RiskMath.multiply(base, fraction);
    }

    // --- loss profile ---------------------------------------------------------------------------

    public static LossProfile lossProfile(RiskCandidate c, RiskPolicy p) {
        BigDecimal entry = c.proposedEntryPrice();
        BigDecimal invalidation = c.structuralInvalidation();
        BigDecimal tick = c.tickSize();
        if (entry == null || entry.signum() <= 0 || invalidation == null || tick == null || tick.signum() <= 0) {
            return LossProfile.invalid(entry, invalidation, RiskReasonCode.INVALID_STOP_DISTANCE);
        }
        BigDecimal buffer = p == null || p.execution() == null || p.execution().adverseSlippageTicks() == null
                ? null
                : tick.multiply(p.execution().adverseSlippageTicks());
        if (buffer == null) {
            return LossProfile.invalid(entry, invalidation, RiskReasonCode.MISSING_POLICY);
        }
        BigDecimal protectiveStop = c.direction().isLong()
                ? RiskMath.floorToTick(invalidation.subtract(buffer), tick)
                : RiskMath.ceilToTick(invalidation.add(buffer), tick);
        // Optional research safety floor: keep the protective stop at least minStopAtr x ATR from
        // entry. This moves only the protective stop (never the strategy's structural invalidation)
        // and re-sizes quantity downstream, so risk stays authoritative.
        BigDecimal minStopAtr = p.execution() == null ? null : p.execution().minStopAtr();
        Double atr = c.referenceAtr();
        if (minStopAtr != null && minStopAtr.signum() > 0 && atr != null && atr > 0) {
            BigDecimal minDistance = RiskMath.money(minStopAtr.multiply(BigDecimal.valueOf(atr)));
            BigDecimal currentDistance = entry.subtract(protectiveStop).abs();
            if (currentDistance.compareTo(minDistance) < 0) {
                protectiveStop = c.direction().isLong()
                        ? RiskMath.floorToTick(entry.subtract(minDistance), tick)
                        : RiskMath.ceilToTick(entry.add(minDistance), tick);
            }
        }
        BigDecimal plannedLoss = c.direction().isLong()
                ? entry.subtract(protectiveStop)
                : protectiveStop.subtract(entry);
        if (plannedLoss.signum() <= 0) {
            return new LossProfile(entry, invalidation, protectiveStop, plannedLoss, null, null, null, null, false,
                    RiskReasonCode.INVALID_STOP_DISTANCE);
        }
        BigDecimal slippageCost = RiskMath.money(
                tick.multiply(nz(p.execution().adverseSlippageTicks())).add(tick.multiply(nz(p.execution().exitCostTicks()))));
        BigDecimal bpsCost = p.execution().exitCostBps() == null
                ? ZERO
                : RiskMath.divide(entry.multiply(p.execution().exitCostBps()), BigDecimal.valueOf(10_000));
        BigDecimal allowance = RiskMath.money(slippageCost.add(bpsCost == null ? ZERO : bpsCost));
        BigDecimal effectiveLoss = RiskMath.money(plannedLoss.add(allowance));
        if (effectiveLoss.signum() <= 0) {
            return new LossProfile(entry, invalidation, protectiveStop, plannedLoss, allowance, effectiveLoss, null, null,
                    false, RiskReasonCode.INVALID_STOP_DISTANCE);
        }
        BigDecimal stressExit = null;
        BigDecimal stressLoss = null;
        if (p.stress() != null && p.stress().enabled() && p.stress().adverseMoveFraction() != null) {
            stressExit = c.direction().isLong()
                    ? entry.multiply(ONE.subtract(p.stress().adverseMoveFraction()))
                    : entry.multiply(ONE.add(p.stress().adverseMoveFraction()));
            BigDecimal stressCosts = nz(p.stress().costPerUnit());
            if (p.stress().costBps() != null) {
                stressCosts = stressCosts.add(RiskMath.divide(entry.multiply(p.stress().costBps()), BigDecimal.valueOf(10_000)));
            }
            BigDecimal adverse = entry.subtract(stressExit).abs();
            stressLoss = RiskMath.money(adverse.add(stressCosts));
            if (stressLoss.signum() <= 0) {
                return new LossProfile(entry, invalidation, protectiveStop, plannedLoss, allowance, effectiveLoss, stressExit,
                        stressLoss, false, RiskReasonCode.INVALID_STOP_DISTANCE);
            }
        }
        return new LossProfile(entry, invalidation, protectiveStop, RiskMath.money(plannedLoss), allowance, effectiveLoss,
                stressExit, stressLoss, true, null);
    }

    // --- budget ---------------------------------------------------------------------------------

    private boolean budgetContextAvailable(RiskContext ctx, RiskPolicy p, Eval e) {
        boolean ok = true;
        if (ctx.riskReferenceEquity() == null || ctx.riskReferenceEquity().signum() <= 0) {
            e.unavailable("context.riskReferenceEquity", ConstraintKind.CURRENCY, RiskReasonCode.DATA_QUALITY_BLOCKED);
            ok = false;
        }
        if (p.trade() == null || p.trade().baseRiskFraction() == null) {
            e.unavailable("policy.trade.baseRiskFraction", ConstraintKind.RATIO, RiskReasonCode.MISSING_POLICY);
            ok = false;
        }
        if (p.portfolio() == null || p.portfolio().sessionRiskBudgetFraction() == null
                || p.portfolio().maxOpenRiskFraction() == null) {
            e.unavailable("policy.portfolio.budget", ConstraintKind.RATIO, RiskReasonCode.MISSING_POLICY);
            ok = false;
        }
        return ok;
    }

    private BudgetResult computeBudget(RiskCandidate c, RiskContext ctx, RiskPolicy p, Eval e, BigDecimal rre) {
        BigDecimal ceiling = fraction(rre, p.trade().baseRiskFraction());
        if (p.trade().absoluteRiskCap() != null) {
            ceiling = ceiling.min(p.trade().absoluteRiskCap());
        }
        BigDecimal strategyCeiling = p.strategyRiskFractions().getOrDefault(
                c.strategyVersion(), p.trade().strategyCeilingFraction());
        if (strategyCeiling != null) {
            ceiling = ceiling.min(fraction(rre, strategyCeiling));
        }
        if (p.trade().deploymentStageCeilingFraction() != null) {
            ceiling = ceiling.min(fraction(rre, p.trade().deploymentStageCeilingFraction()));
        }

        Map<String, BigDecimal> pre = new LinkedHashMap<>();
        pre.put("trade.ceiling", ceiling);
        BigDecimal sessionLimit = fraction(rre, p.portfolio().sessionRiskBudgetFraction());
        BigDecimal sessionRemaining = sessionLimit.subtract(nz(ctx.sessionRealizedLoss())).subtract(nz(ctx.reservedRisk()));
        pre.put("session.remaining", sessionRemaining);

        BigDecimal portfolioOpenLimit = fraction(rre, p.portfolio().maxOpenRiskFraction());
        BigDecimal portfolioOpenRemaining = portfolioOpenLimit.subtract(nz(ctx.portfolioOpenRisk())).subtract(nz(ctx.reservedRisk()));
        pre.put("portfolio.openRisk.remaining", portfolioOpenRemaining);

        BigDecimal strategyFraction = p.strategyRiskBudgetFractions().get(c.strategyVersion());
        BigDecimal strategyLimit = strategyFraction != null
                ? fraction(rre, strategyFraction)
                : fraction(rre, p.portfolio().portfolioRiskBudgetFraction());
        RiskContext.KeyRisk strategyRisk = ctx.keyRisk("strategy:" + c.strategyVersion());
        BigDecimal strategyRemaining = strategyLimit == null
                ? null
                : strategyLimit.subtract(nz(strategyRisk.realizedLoss()))
                        .subtract(nz(strategyRisk.openRisk()))
                        .subtract(nz(strategyRisk.reservedRisk()));
        pre.put("strategy.remaining", strategyRemaining);

        BigDecimal symbolLimit = p.symbolRiskBudgetFractions().containsKey(String.valueOf(c.instrumentId()))
                ? fraction(rre, p.symbolRiskBudgetFractions().get(String.valueOf(c.instrumentId())))
                : fraction(rre, p.symbol() == null ? null : p.symbol().riskBudgetFraction());
        RiskContext.KeyRisk symbolRisk = ctx.keyRisk("symbol:" + c.instrumentId());
        BigDecimal symbolRemaining = symbolLimit == null
                ? null
                : symbolLimit.subtract(nz(symbolRisk.realizedLoss()))
                        .subtract(nz(symbolRisk.openRisk()))
                        .subtract(nz(symbolRisk.reservedRisk()));
        pre.put("symbol.remaining", symbolRemaining);

        BigDecimal sectorLimit = p.sectorRiskBudgetFractions().containsKey(String.valueOf(c.sectorCode()))
                ? fraction(rre, p.sectorRiskBudgetFractions().get(String.valueOf(c.sectorCode())))
                : fraction(rre, p.sector() == null ? null : p.sector().riskBudgetFraction());
        RiskContext.KeyRisk sectorRisk = ctx.keyRisk("sector:" + c.sectorCode());
        BigDecimal sectorRemaining = sectorLimit == null
                ? null
                : sectorLimit.subtract(nz(sectorRisk.realizedLoss()))
                        .subtract(nz(sectorRisk.openRisk()))
                        .subtract(nz(sectorRisk.reservedRisk()));
        pre.put("sector.remaining", sectorRemaining);

        if (sessionRemaining.signum() < 0 || portfolioOpenRemaining.signum() < 0) {
            e.reasons.add(RiskReasonCode.PORTFOLIO_OPEN_RISK_LIMIT);
        }
        if (strategyRemaining != null && strategyRemaining.signum() < 0) {
            e.reasons.add(RiskReasonCode.TRADE_RISK_LIMIT);
        }
        if (symbolRemaining != null && symbolRemaining.signum() < 0) {
            e.reasons.add(RiskReasonCode.SYMBOL_CONCENTRATION_LIMIT);
        }
        if (sectorRemaining != null && sectorRemaining.signum() < 0) {
            e.reasons.add(RiskReasonCode.SECTOR_CONCENTRATION_LIMIT);
        }

        BigDecimal budget = ceiling;
        budget = min(budget, sessionRemaining);
        budget = min(budget, portfolioOpenRemaining);
        budget = min(budget, strategyRemaining);
        budget = min(budget, symbolRemaining);
        budget = min(budget, sectorRemaining);
        budget = budget.max(ZERO);

        // Risk-reduction modifiers, applied exactly once (DD-03 section 217).
        RiskPolicy.RiskStateMultiplier stateMultiplier = p.stateModifier(ctx.riskState());
        if (stateMultiplier == null) {
            e.unavailable("policy.stateModifier." + ctx.riskState(), ConstraintKind.RATIO, RiskReasonCode.MISSING_POLICY);
            return new BudgetResult(ZERO, pre, ceiling);
        }
        BigDecimal multiplier = stateMultiplier.value();
        if (ctx.riskState() != RiskState.NORMAL) {
            e.reasons.add(RiskReasonCode.RISK_REDUCED_DRAWDOWN);
        }
        if (p.correlationEnabled()) {
            BigDecimal correlation = p.correlationModifiers().getOrDefault(c.sectorCode(), ONE);
            if (correlation.signum() <= 0 || correlation.compareTo(ONE) > 0) {
                e.unavailable("policy.correlation", ConstraintKind.RATIO, RiskReasonCode.MISSING_POLICY);
                return new BudgetResult(ZERO, pre, ceiling);
            }
            if (correlation.compareTo(ONE) < 0) {
                multiplier = multiplier.multiply(correlation);
                e.reasons.add(RiskReasonCode.RISK_REDUCED_CORRELATION);
            }
        }
        BigDecimal adjusted = RiskMath.money(budget.multiply(multiplier));
        adjusted = adjusted.min(ceiling); // every hard ceiling rechecked after modifiers
        return new BudgetResult(adjusted.max(ZERO), pre, ceiling);
    }

    private record BudgetResult(BigDecimal budget, Map<String, BigDecimal> preCapacity, BigDecimal ceiling) {
    }

    // --- quantities -----------------------------------------------------------------------------

    private QuantityPlan quantities(
            RiskCandidate c, RiskContext ctx, RiskPolicy p, Eval e, BigDecimal rre, LossProfile loss, long riskSized) {
        List<QuantityCap> caps = new ArrayList<>();
        BigDecimal entry = c.proposedEntryPrice();
        BigDecimal effectiveLoss = loss.effectiveLossPerUnit();
        RiskContext.SymbolExposure symbol = ctx.symbol(c.instrumentId());
        RiskContext.KeyRisk symbolRisk = ctx.keyRisk("symbol:" + c.instrumentId());
        RiskContext.KeyRisk sectorRisk = ctx.keyRisk("sector:" + c.sectorCode());

        caps.add(QuantityCap.of("riskSized", riskSized));

        if (c.requestedQuantitySupplied()) {
            caps.add(QuantityCap.of("requested", c.requestedQuantity()));
        }

        // Maximum position notional.
        BigDecimal maxNotional = fraction(rre, p.trade().maxPositionNotionalFraction());
        if (maxNotional == null) {
            e.unavailable("quantity.maxPositionNotional", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            long qty = RiskMath.floorQuantity(maxNotional, entry);
            caps.add(QuantityCap.of("maxPositionNotional", qty).withReason(RiskReasonCode.MAX_POSITION_NOTIONAL_LIMIT));
            e.pass("notional.maxPosition", ConstraintKind.CURRENCY,
                    RiskMath.money(entry.multiply(BigDecimal.valueOf(qty))), maxNotional, null);
        }

        // Gross exposure.
        if (p.portfolio().maxGrossExposureFraction() == null) {
            e.unavailable("quantity.grossExposure", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            BigDecimal limit = fraction(rre, p.portfolio().maxGrossExposureFraction());
            BigDecimal remaining = limit.subtract(nz(ctx.grossExposure())).subtract(nz(ctx.reservedNotional()));
            long qty = RiskMath.floorQuantity(remaining.max(ZERO), entry);
            caps.add(QuantityCap.of("grossExposure", qty).withReason(RiskReasonCode.GROSS_EXPOSURE_LIMIT));
            record(e, "exposure.gross", ctx.grossExposure(), limit, remaining, RiskReasonCode.GROSS_EXPOSURE_LIMIT);
        }

        // Net exposure (handles same-sign increase, reduction, and crossing zero).
        if (p.portfolio().maxNetExposureFraction() == null) {
            e.unavailable("quantity.netExposure", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            BigDecimal limit = fraction(rre, p.portfolio().maxNetExposureFraction());
            BigDecimal capacity = directionalNetCapacity(ctx.netExposure(), c.direction(), limit);
            long qty = RiskMath.floorQuantity(capacity, entry);
            caps.add(QuantityCap.of("netExposure", qty).withReason(RiskReasonCode.NET_EXPOSURE_LIMIT));
            record(e, "exposure.net", ctx.netExposure(), limit, capacity, RiskReasonCode.NET_EXPOSURE_LIMIT);
        }

        // Liquidity participation.
        if (c.expectedExecutableVolume() == null || p.liquidity() == null
                || p.liquidity().maxParticipationFraction() == null) {
            if (p.liquidity() != null && p.liquidity().liquidityMandatory()) {
                e.unavailable("quantity.liquidity", ConstraintKind.QUANTITY, RiskReasonCode.LIQUIDITY_LIMIT);
            } else {
                caps.add(QuantityCap.notApplicable("liquidity"));
            }
        } else {
            BigDecimal executable = BigDecimal.valueOf(c.expectedExecutableVolume());
            long qty = RiskMath.floorQuantity(RiskMath.money(executable.multiply(p.liquidity().maxParticipationFraction())), ONE);
            caps.add(QuantityCap.of("liquidity", qty).withReason(RiskReasonCode.LIQUIDITY_LIMIT));
            e.pass("liquidity.participation", ConstraintKind.QUANTITY, executable, executable, null);
        }

        // Symbol concentration: notional and open risk.
        if (p.symbol() == null || p.symbol().maxNotionalFraction() == null) {
            e.unavailable("quantity.symbolNotional", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            BigDecimal limit = fraction(rre, p.symbol().maxNotionalFraction());
            BigDecimal remaining = limit.subtract(nz(symbol == null ? null : symbol.grossNotional()))
                    .subtract(nz(symbolRisk.reservedNotional()));
            long qty = RiskMath.floorQuantity(remaining.max(ZERO), entry);
            caps.add(QuantityCap.of("symbolNotional", qty).withReason(RiskReasonCode.SYMBOL_CONCENTRATION_LIMIT));
            record(e, "symbol.notional", symbol == null ? null : symbol.grossNotional(), limit, remaining,
                    RiskReasonCode.SYMBOL_CONCENTRATION_LIMIT);
        }
        if (p.symbol() == null || p.symbol().maxOpenRiskFraction() == null) {
            e.unavailable("quantity.symbolOpenRisk", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            BigDecimal limit = fraction(rre, p.symbol().maxOpenRiskFraction());
            BigDecimal remaining = limit.subtract(nz(symbolRisk.openRisk())).subtract(nz(symbolRisk.reservedRisk()));
            long qty = RiskMath.floorQuantity(remaining.max(ZERO), effectiveLoss);
            caps.add(QuantityCap.of("symbolOpenRisk", qty).withReason(RiskReasonCode.SYMBOL_CONCENTRATION_LIMIT));
            record(e, "symbol.openRisk", symbolRisk.openRisk(), limit, remaining, RiskReasonCode.SYMBOL_CONCENTRATION_LIMIT);
        }

        // Sector concentration: notional, open risk, directional risk.
        if (p.sector() == null || p.sector().maxNotionalFraction() == null) {
            e.unavailable("quantity.sectorNotional", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            BigDecimal limit = fraction(rre, p.sector().maxNotionalFraction());
            BigDecimal remaining = limit.subtract(nz(sectorRisk.grossNotional())).subtract(nz(sectorRisk.reservedNotional()));
            long qty = RiskMath.floorQuantity(remaining.max(ZERO), entry);
            caps.add(QuantityCap.of("sectorNotional", qty).withReason(RiskReasonCode.SECTOR_CONCENTRATION_LIMIT));
            record(e, "sector.notional", sectorRisk.grossNotional(), limit, remaining,
                    RiskReasonCode.SECTOR_CONCENTRATION_LIMIT);
        }
        if (p.sector() == null || p.sector().maxOpenRiskFraction() == null) {
            e.unavailable("quantity.sectorOpenRisk", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
        } else {
            BigDecimal limit = fraction(rre, p.sector().maxOpenRiskFraction());
            BigDecimal remaining = limit.subtract(nz(sectorRisk.openRisk())).subtract(nz(sectorRisk.reservedRisk()));
            long qty = RiskMath.floorQuantity(remaining.max(ZERO), effectiveLoss);
            caps.add(QuantityCap.of("sectorOpenRisk", qty).withReason(RiskReasonCode.SECTOR_CONCENTRATION_LIMIT));
            record(e, "sector.openRisk", sectorRisk.openRisk(), limit, remaining, RiskReasonCode.SECTOR_CONCENTRATION_LIMIT);
        }
        if (p.sector() != null && p.sector().maxDirectionalRiskFraction() != null) {
            BigDecimal limit = fraction(rre, p.sector().maxDirectionalRiskFraction());
            BigDecimal capacity = directionalNetCapacity(sectorRisk.netNotional(), c.direction(), limit);
            long qty = RiskMath.floorQuantity(capacity, entry);
            caps.add(QuantityCap.of("sectorDirectional", qty).withReason(RiskReasonCode.SECTOR_CONCENTRATION_LIMIT));
            record(e, "sector.directional", sectorRisk.netNotional(), limit, capacity,
                    RiskReasonCode.SECTOR_CONCENTRATION_LIMIT);
        }

        // Correlation as a reducer only: no second quantity cap (explicit, documented decision).
        caps.add(QuantityCap.notApplicable("correlation"));

        // Broker permitted quantity.
        if (c.brokerMaxQuantity() != null) {
            caps.add(QuantityCap.of("brokerPermitted", c.brokerMaxQuantity()).withReason(RiskReasonCode.BROKER_QUANTITY_LIMIT));
        } else if (p.liquidity() != null && p.liquidity().brokerCapMandatory()) {
            e.unavailable("quantity.broker", ConstraintKind.QUANTITY, RiskReasonCode.BROKER_QUANTITY_LIMIT);
        } else {
            caps.add(QuantityCap.notApplicable("brokerPermitted"));
        }

        // Buying power and buffered margin.
        if (ctx.buyingPower() == null) {
            e.unavailable("quantity.buyingPower", ConstraintKind.CURRENCY, RiskReasonCode.BUYING_POWER_LIMIT);
        } else {
            long qty = RiskMath.floorQuantity(ctx.buyingPower(), entry);
            caps.add(QuantityCap.of("buyingPower", qty).withReason(RiskReasonCode.BUYING_POWER_LIMIT));
            record(e, "funding.buyingPower", entry, ctx.buyingPower(), ctx.buyingPower(), RiskReasonCode.BUYING_POWER_LIMIT);
            BigDecimal buffer = p.margin() == null || p.margin().safetyBufferFraction() == null
                    ? ZERO
                    : p.margin().safetyBufferFraction();
            BigDecimal usable = RiskMath.money(ctx.buyingPower().multiply(ONE.subtract(buffer)));
            long marginQty = RiskMath.floorQuantity(usable.max(ZERO), entry);
            caps.add(QuantityCap.of("margin", marginQty).withReason(RiskReasonCode.MARGIN_LIMIT));
        }

        // Stress permitted quantity.
        if (p.stress() != null && p.stress().enabled() && p.stress().adverseMoveFraction() != null
                && loss.stressLossPerUnit() != null) {
            if (p.portfolio().maxStressRiskFraction() == null) {
                e.unavailable("quantity.stress", ConstraintKind.CURRENCY, RiskReasonCode.MISSING_POLICY);
            } else {
                BigDecimal limit = fraction(rre, p.portfolio().maxStressRiskFraction());
                BigDecimal remaining = limit.subtract(nz(ctx.portfolioStressRisk())).subtract(nz(ctx.reservedRisk()));
                long qty = RiskMath.floorQuantity(remaining.max(ZERO), loss.stressLossPerUnit());
                caps.add(QuantityCap.of("stress", qty).withReason(RiskReasonCode.PORTFOLIO_STRESS_RISK_LIMIT));
                record(e, "risk.portfolioStress", ctx.portfolioStressRisk(), limit, remaining,
                        RiskReasonCode.PORTFOLIO_STRESS_RISK_LIMIT);
            }
        } else {
            caps.add(QuantityCap.notApplicable("stress"));
        }

        // Maximum simultaneous positions (gate, not a per-share cap).
        if (p.portfolio().maxPositions() != null && p.portfolio().maxPositions() > 0) {
            boolean newSymbol = symbol == null || symbol.signedQuantity() == 0;
            if (newSymbol && ctx.openPositionCount() >= p.portfolio().maxPositions()) {
                e.fail("portfolio.maxPositions", ConstraintKind.COUNT,
                        BigDecimal.valueOf(ctx.openPositionCount()),
                        BigDecimal.valueOf(p.portfolio().maxPositions()), ZERO,
                        RiskReasonCode.MAX_POSITIONS_LIMIT, true);
            }
        }

        long max = Long.MAX_VALUE;
        for (QuantityCap cap : caps) {
            if (cap.applicable()) {
                max = Math.min(max, cap.quantity());
            }
        }
        List<QuantityCap> adjusted = new ArrayList<>(caps);
        adjusted.add(QuantityCap.of("final", Math.max(max, 0)));
        return new QuantityPlan(adjusted, Math.max(max, 0));
    }

    private static BigDecimal directionalNetCapacity(BigDecimal currentNet, Direction direction, BigDecimal limit) {
        BigDecimal net = nz(currentNet);
        boolean sameSign = (direction.isLong() && net.signum() >= 0) || (!direction.isLong() && net.signum() <= 0);
        BigDecimal capacity = sameSign ? limit.subtract(net.abs()) : limit.add(net.abs());
        return capacity.max(ZERO);
    }

    private static void record(
            Eval e, String name, BigDecimal actual, BigDecimal limit, BigDecimal remaining, RiskReasonCode code) {
        if (actual != null && limit != null && actual.compareTo(limit) > 0) {
            e.fail(name, ConstraintKind.CURRENCY, actual, limit, remaining, code, true);
        } else {
            e.pass(name, ConstraintKind.CURRENCY, actual, limit, remaining);
        }
    }

    // --- output ---------------------------------------------------------------------------------

    private RiskDecisionProposal reject(
            RiskCandidate c, RiskContext ctx, RiskPolicy p, Eval e, LossProfile loss) {
        RiskDecisionType decision = e.halt ? RiskDecisionType.HALT_REQUIRED : RiskDecisionType.REJECT;
        return build(c, ctx, p, e, loss, decision, new BudgetResult(ZERO, Map.of(), ZERO),
                0, 0, ZERO, ZERO, ZERO, ZERO, ZERO, List.of(), Map.of());
    }

    private RiskDecisionProposal build(
            RiskCandidate c,
            RiskContext ctx,
            RiskPolicy policy,
            Eval e,
            LossProfile loss,
            RiskDecisionType decision,
            BudgetResult budget,
            long riskSized,
            long approved,
            BigDecimal approvedNotional,
            BigDecimal nominalRisk,
            BigDecimal execRisk,
            BigDecimal stressRisk,
            BigDecimal marginRequirement,
            List<QuantityCap> caps,
            Map<String, BigDecimal> preCapacity) {
        List<RiskReasonCode> reasons = List.copyOf(e.reasons);
        RiskReasonCode primary = reasons.isEmpty() ? null : reasons.get(0);
        Map<String, BigDecimal> projected = new LinkedHashMap<>(preCapacity);
        projected.put("projected.executionAdjustedRisk", execRisk);
        projected.put("projected.notional", approvedNotional);
        Long requested = c.requestedQuantity();
        String explanation = explain(decision, reasons, loss, approved, riskSized);
        return new RiskDecisionProposal(
                decisionKey(c, ctx, policy),
                c.candidateKey(),
                c.candidateId(),
                ctx.session() == null || ctx.session().now() == null ? Instant.EPOCH : ctx.session().now(),
                c.mode(),
                policy == null ? null : policy.code(),
                policy == null ? 0 : policy.version(),
                policy == null ? null : policy.lifecycleState(),
                ctx.contextKey(),
                ctx.contextVersion(),
                c.setupObservationId(),
                c.setupInstanceId(),
                c.strategyVersionId(),
                c.brokerAccountId(),
                c.instrumentId(),
                c.symbol(),
                c.direction(),
                ctx.riskState(),
                decision,
                requested,
                riskSized,
                approved,
                loss.entryPrice(),
                loss.structuralInvalidation(),
                loss.protectiveStop(),
                loss.plannedLossPerUnit(),
                loss.effectiveLossPerUnit(),
                loss.stressLossPerUnit(),
                nominalRisk,
                execRisk,
                stressRisk,
                approvedNotional,
                marginRequirement,
                budget.budget(),
                ctx.riskReferenceEquity(),
                preCapacity,
                projected,
                caps,
                e.constraints,
                reasons,
                primary,
                explanation);
    }

    /** Stable idempotency identity: same candidate key, context version, and policy version. */
    public static String decisionKey(RiskCandidate c, RiskContext ctx, RiskPolicy p) {
        String seed = String.join("|",
                "risk-decision",
                String.valueOf(c.brokerAccountId()),
                c.mode() == null ? "" : c.mode().name(),
                c.candidateKey() == null ? "" : c.candidateKey(),
                String.valueOf(c.setupObservationId()),
                ctx == null ? "" : ctx.contextKey(),
                ctx == null ? "0" : String.valueOf(ctx.contextVersion()),
                p == null ? "" : p.code(),
                p == null ? "0" : String.valueOf(p.version()));
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String explain(
            RiskDecisionType decision, List<RiskReasonCode> reasons, LossProfile loss, long approved, long riskSized) {
        StringBuilder builder = new StringBuilder(decision.name());
        builder.append(" approved=").append(approved).append(" riskSized=").append(riskSized);
        if (loss.valid()) {
            builder.append(" stop=").append(loss.protectiveStop())
                    .append(" effectiveLossPerUnit=").append(loss.effectiveLossPerUnit());
        }
        if (!reasons.isEmpty()) {
            builder.append(" reasons=").append(reasons);
        }
        return builder.toString();
    }

    private static QuantityCap markBinding(QuantityCap cap, long finalQuantity, long riskSized) {
        boolean structural = "riskSized".equals(cap.name()) || "final".equals(cap.name());
        return !structural && cap.applicable() && cap.quantity() == finalQuantity && finalQuantity <= riskSized
                ? cap.asBinding()
                : cap;
    }

    private static boolean trusted(String value, String... accepted) {
        if (value == null) {
            return false;
        }
        for (String candidate : accepted) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.min(b);
    }

    private record QuantityPlan(List<QuantityCap> caps, long maxQuantity) {

        RiskReasonCode firstBindingReason() {
            RiskReasonCode best = null;
            for (QuantityCap cap : caps) {
                if (!cap.applicable() || cap.quantity() != maxQuantity || cap.reasonCode() == null) {
                    continue;
                }
                if (best == null || RiskReasonCode.deterministicOrder().compare(cap.reasonCode(), best) < 0) {
                    best = cap.reasonCode();
                }
            }
            return best;
        }
    }

    private static final class Eval {
        final List<ConstraintEvaluation> constraints = new ArrayList<>();
        final Set<RiskReasonCode> reasons = new TreeSet<>(RiskReasonCode.deterministicOrder());
        boolean fatal;
        boolean halt;

        void pass(String name, ConstraintKind kind, BigDecimal actual, BigDecimal limit, BigDecimal remaining) {
            constraints.add(ConstraintEvaluation.pass(name, kind, actual, limit, remaining));
        }

        void gate(String name, ConstraintKind kind, boolean passed, RiskReasonCode code) {
            if (passed) {
                pass(name, kind, ONE, ONE, ZERO);
            } else {
                fail(name, kind, ZERO, ONE, ZERO, code, true);
            }
        }

        void fail(
                String name,
                ConstraintKind kind,
                BigDecimal actual,
                BigDecimal limit,
                BigDecimal remaining,
                RiskReasonCode code,
                boolean fatalFailure) {
            constraints.add(ConstraintEvaluation.fail(name, kind, actual, limit, remaining, code, true));
            reasons.add(code);
            if (fatalFailure) {
                fatal = true;
            }
        }

        void unavailable(String name, ConstraintKind kind, RiskReasonCode code) {
            constraints.add(ConstraintEvaluation.unavailable(name, kind, code));
            reasons.add(code);
            fatal = true;
        }
    }
}
