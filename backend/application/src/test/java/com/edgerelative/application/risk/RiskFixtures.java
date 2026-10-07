package com.edgerelative.application.risk;

import com.edgerelative.application.risk.domain.LossProfile;
import com.edgerelative.application.risk.domain.PolicyState;
import com.edgerelative.application.risk.domain.RiskCandidate;
import com.edgerelative.application.risk.domain.RiskContext;
import com.edgerelative.application.risk.domain.RiskPolicy;
import com.edgerelative.application.risk.domain.RiskState;
import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;

/**
 * Synthetic, clearly isolated fixtures. These values are calibrated for tests only; they are never
 * loaded as production policy.
 */
public final class RiskFixtures {

    public static final Instant T = Instant.parse("2026-09-18T04:30:00Z");
    public static final LocalDate SESSION = LocalDate.of(2026, 9, 18);
    public static final String STRATEGY_VERSION = "ER_RS_CONTINUATION_V1/v1";

    private RiskFixtures() {
    }

    public static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    public static RiskPolicy policy() {
        return new RiskPolicy(
                "ER_RISK_V1_SYNTHETIC",
                1,
                PolicyState.VALIDATED,
                EnumSet.allOf(com.edgerelative.application.risk.domain.TradingMode.class),
                Map.of(),
                new RiskPolicy.TradeLimits(dec("0.01"), null, null, null, dec("0.20")),
                new RiskPolicy.PortfolioLimits(
                        dec("0.05"), dec("0.10"), dec("1.0"), dec("0.5"), dec("0.03"), dec("0.05"), 5),
                new RiskPolicy.SymbolLimits(dec("0.20"), dec("0.02"), dec("0.02"), dec("0.01")),
                new RiskPolicy.SectorLimits(dec("0.40"), dec("0.03"), dec("0.30"), dec("0.03"), true),
                new RiskPolicy.MarginLimits(dec("0.10")),
                new RiskPolicy.LiquidityLimits(dec("0.02"), dec("25"), true, false, true),
                new RiskPolicy.ExecutionAssumptions(dec("1"), dec("1"), dec("5"), null),
                new RiskPolicy.StressAssumptions(dec("0.03"), dec("0"), dec("5"), true),
                new RiskPolicy.DrawdownLimits(
                        dec("0.01"), dec("0.02"), dec("0.03"), dec("0.05"), dec("0.08"), dec("0.10"), 4),
                Map.of(RiskState.REDUCED_1, dec("0.5"), RiskState.REDUCED_2, dec("0.25")),
                Map.of(),
                Map.of(STRATEGY_VERSION, dec("0.03")),
                Map.of(),
                Map.of("IT", dec("0.03")),
                false,
                Map.of(),
                false,
                false,
                false,
                BigDecimal.ONE);
    }

    public static RiskContext normalContext() {
        return RiskContext.builder("ctx-1", 1, T, SESSION)
                .available()
                .equity(dec("1000000"), dec("1000000"))
                .funding(dec("500000"), dec("500000"), dec("0"))
                .exposures(dec("0"), dec("0"))
                .risk(dec("0"), dec("0"), dec("0"), dec("0"))
                .losses(dec("0"), dec("0"), dec("0"), dec("0"), dec("0"))
                .state(RiskState.NORMAL)
                .counters(0, 0)
                .health("HEALTHY", "HEALTHY", "MATCHED", true)
                .session(new RiskContext.SessionWindow(T, true, true, false, false, false, "nse-session-v1"))
                .symbols(Map.of())
                .keyRisk(Map.of())
                .build();
    }

    public static RiskContext.Builder contextBuilder() {
        return RiskContext.builder("ctx-1", 1, T, SESSION)
                .available()
                .equity(dec("1000000"), dec("1000000"))
                .funding(dec("500000"), dec("500000"), dec("0"))
                .exposures(dec("0"), dec("0"))
                .risk(dec("0"), dec("0"), dec("0"), dec("0"))
                .losses(dec("0"), dec("0"), dec("0"), dec("0"), dec("0"))
                .state(RiskState.NORMAL)
                .counters(0, 0)
                .health("HEALTHY", "HEALTHY", "MATCHED", true)
                .session(new RiskContext.SessionWindow(T, true, true, false, false, false, "nse-session-v1"))
                .symbols(Map.of())
                .keyRisk(Map.of());
    }

    public static LossProfile longLoss(RiskCandidate candidate, RiskPolicy policy) {
        return com.edgerelative.application.risk.domain.RiskEvaluator.lossProfile(candidate, policy);
    }

    public static Cand candidate() {
        return new Cand();
    }

    public static final class Cand {
        private String candidateKey = "cand-1";
        private long tenantId = 1;
        private long brokerAccountId = 1;
        private com.edgerelative.application.risk.domain.TradingMode mode =
                com.edgerelative.application.risk.domain.TradingMode.ASSISTED_LIVE;
        private String setupInstanceId = "11111111-1111-1111-1111-111111111111";
        private long setupObservationId = 42;
        private int strategyVersionId = 7;
        private long instrumentId = 100;
        private String symbol = "TCS";
        private Direction direction = Direction.LONG;
        private BigDecimal tickSize = dec("0.05");
        private long quantityIncrement = 1;
        private BigDecimal entry = dec("100");
        private BigDecimal invalidation = dec("98");
        private String invalidationBasis = "M5 swing low";
        private String marketRegime = "BULLISH";
        private boolean marketRegimeKnown = true;
        private boolean eventRiskKnown = true;
        private boolean eventRiskBlocked = false;
        private Long sectorId = 1L;
        private String sectorCode = "IT";
        private Double spreadBps = 5.0;
        private Double expectedExecutableVolume = 100000.0;
        private Long brokerMaxQuantity = null;
        private Long requestedQuantity = null;
        private boolean setupValid = true;
        private String setupStatus = "VALID";
        private Double referenceAtr = null;

        public Cand direction(Direction value) {
            this.direction = value;
            return this;
        }

        public Cand entry(String value) {
            this.entry = dec(value);
            return this;
        }

        public Cand invalidation(String value) {
            this.invalidation = dec(value);
            return this;
        }

        public Cand symbol(String value) {
            this.symbol = value;
            return this;
        }

        public Cand sector(String code, Long id) {
            this.sectorCode = code;
            this.sectorId = id;
            return this;
        }

        public Cand requested(long value) {
            this.requestedQuantity = value;
            return this;
        }

        public Cand brokerMax(long value) {
            this.brokerMaxQuantity = value;
            return this;
        }

        public Cand spread(Double value) {
            this.spreadBps = value;
            return this;
        }

        public Cand executableVolume(Double value) {
            this.expectedExecutableVolume = value;
            return this;
        }

        public Cand setupValid(boolean value) {
            this.setupValid = value;
            return this;
        }

        public Cand setupStatus(String value) {
            this.setupStatus = value;
            return this;
        }

        public Cand eventBlocked(boolean value) {
            this.eventRiskBlocked = value;
            return this;
        }

        public Cand referenceAtr(Double value) {
            this.referenceAtr = value;
            return this;
        }

        public Cand mode(com.edgerelative.application.risk.domain.TradingMode value) {
            this.mode = value;
            return this;
        }

        public Cand instrument(long id) {
            this.instrumentId = id;
            return this;
        }

        public RiskCandidate build() {
            return new RiskCandidate(
                    candidateKey, "cand", tenantId, brokerAccountId, mode, setupInstanceId, setupObservationId,
                    "ER_RS_CONTINUATION_V1", STRATEGY_VERSION, strategyVersionId, instrumentId, symbol, direction,
                    tickSize, quantityIncrement, entry, invalidation, invalidationBasis, T, marketRegime,
                    marketRegimeKnown, eventRiskKnown, eventRiskBlocked, sectorId, sectorCode, SESSION, spreadBps,
                    expectedExecutableVolume, 5_000_000.0, brokerMaxQuantity, requestedQuantity, setupValid,
                    setupStatus, "er-feature-schema-v1", "ER_RISK_V1_SYNTHETIC", referenceAtr);
        }
    }
}
