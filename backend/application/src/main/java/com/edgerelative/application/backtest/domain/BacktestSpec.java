package com.edgerelative.application.backtest.domain;

import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resolved, versioned backtest specification. Persisted before execution begins so a rerun is
 * reproducible from the same inputs. Every nullable policy value means "not configured" and the
 * engine must fail that input closed rather than inventing a default.
 */
public record BacktestSpec(
        String runKey,
        List<Long> instrumentIds,
        List<String> symbols,
        LocalDate startDate,
        LocalDate endDate,
        String timeframe,
        String dailyTimeframe,
        BigDecimal startingCapital,
        String currency,
        boolean strictProducers,
        StrategyParameters strategyParameters,
        RiskPolicy riskPolicy,
        FeaturePolicy featurePolicy,
        ExecutionPolicy execution,
        CostSchedule costSchedule,
        EndOfRunPolicy endOfRun,
        int warmupSessions,
        long seed,
        String engineRevision,
        Long marketInstrumentId,
        Long sectorInstrumentId,
        String datasetCode,
        String datasetChecksum,
        ContextSource contextSource) {

    public BacktestSpec {
        instrumentIds = instrumentIds == null ? List.of() : List.copyOf(instrumentIds);
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
    }

    /**
     * Where the strategy's market/stock/structure inputs come from. {@code STRICT_PRODUCTION} uses
     * real producers (none wired yet → fails closed, no trades). {@code DERIVED_RESEARCH} derives
     * them deterministically from canonical data under documented, versioned research assumptions.
     */
    public enum ContextSource {
        STRICT_PRODUCTION,
        DERIVED_RESEARCH
    }

    public enum EndOfRunPolicy {
        /** Retain open positions and mark them to market at the final close. */
        MARK_TO_MARKET,
        /** Liquidate open positions at the final close with the configured cost schedule. */
        LIQUIDATE_AT_CLOSE
    }

    /** Deterministic, versioned execution policy (conservative OHLC-only rules). */
    public record ExecutionPolicy(
            String version,
            long latencySeconds,
            BigDecimal halfSpreadBps,
            BigDecimal adverseSlippageBps,
            BigDecimal participationRate,
            int orderExpiryBars,
            SessionCutoff sessionCutoff,
            AmbiguityPolicy ambiguityPolicy,
            boolean allowOvernight,
            EntryMethod entryMethod,
            TargetMethod targetMethod,
            BigDecimal targetR,
            Double minStopAtr) {

        public ExecutionPolicy {
            ambiguityPolicy = ambiguityPolicy == null ? AmbiguityPolicy.STOP_FIRST_CONSERVATIVE : ambiguityPolicy;
            entryMethod = entryMethod == null ? EntryMethod.MARKET_NEXT_OPEN : entryMethod;
            targetMethod = targetMethod == null ? TargetMethod.NONE : targetMethod;
        }

        public enum AmbiguityPolicy {
            /** When stop and target are both touched in one bar, assume the stop is hit first. */
            STOP_FIRST_CONSERVATIVE,
            /** Assume the target is hit first (optimistic diagnostic). */
            TARGET_FIRST_OPTIMISTIC,
            /** Skip the trade entirely when one bar touches both stop and target. */
            SKIP_AMBIGUOUS
        }

        /** How a pending entry becomes a fill on the next eligible bar(s). */
        public enum EntryMethod {
            /** Fill at the next eligible bar's open (baseline). */
            MARKET_NEXT_OPEN,
            /** Fill only if price reaches the plan's entry trigger; a stop-entry fill. */
            TRIGGER_LIMIT
        }

        /** Where the exit target comes from. */
        public enum TargetMethod {
            /** No target (documented target producer not wired): stop/session exits only. */
            NONE,
            /** Target at a fixed multiple of the planned risk distance (e.g. 2R). */
            R_MULTIPLE
        }
    }

    public enum SessionCutoff {
        /** Do not open new positions after this many minutes before session close. */
        NEW_ENTRY_CUTOFF,
        /** Flatten all positions at session close. */
        SESSION_FLATTEN
    }

    /**
     * Versioned, itemized Indian-market cost schedule. Rates are user-supplied assumptions; no
     * current fee rate is hardcoded. All values are fractions (0.0005 = 0.05%).
     */
    public record CostSchedule(
            String version,
            BigDecimal brokerageBuyBps,
            BigDecimal brokerageSellBps,
            BigDecimal sttSellBps,
            BigDecimal exchangeBps,
            BigDecimal gstOnBrokerageAndExchangeBps,
            BigDecimal sebiBps,
            BigDecimal stampDutyBuyBps,
            BigDecimal otherBuyBps,
            BigDecimal otherSellBps,
            boolean roundTripCosts) {
    }

}
