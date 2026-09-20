package com.edgerelative.application.risk.domain;

import com.edgerelative.application.strategy.domain.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Immutable, authoritative risk context at a point in time (DD-03 §30). Missing data stays missing:
 * callers must set the availability flags false rather than substituting zero balances, zero
 * exposure, healthy broker status, or an empty portfolio.
 *
 * <p>{@code symbols} and {@code keyRisk} are supplied by the orchestration layer from positions,
 * pending orders, active reservations, and realized losses. The evaluator performs no aggregation
 * I/O and no lookups.
 */
public record RiskContext(
        String contextKey,
        long contextVersion,
        Instant capturedAt,
        LocalDate tradingDate,
        boolean accountAvailable,
        boolean portfolioAvailable,
        boolean brokerAvailable,
        BigDecimal riskReferenceEquity,
        BigDecimal currentNetLiquidationValue,
        BigDecimal availableCash,
        BigDecimal buyingPower,
        BigDecimal marginUsed,
        BigDecimal grossExposure,
        BigDecimal netExposure,
        BigDecimal portfolioOpenRisk,
        BigDecimal portfolioStressRisk,
        BigDecimal reservedRisk,
        BigDecimal reservedNotional,
        BigDecimal sessionRealizedLoss,
        BigDecimal sessionDrawdown,
        BigDecimal accountDrawdown,
        BigDecimal weeklyDrawdown,
        BigDecimal monthlyDrawdown,
        RiskState riskState,
        long openPositionCount,
        int consecutiveLosses,
        String brokerHealth,
        String dataHealth,
        String reconciliationState,
        boolean orderStateKnown,
        SessionWindow session,
        Map<Long, SymbolExposure> symbols,
        Map<String, KeyRisk> keyRisk) {

    public RiskContext {
        symbols = symbols == null ? Map.of() : Map.copyOf(symbols);
        keyRisk = keyRisk == null ? Map.of() : Map.copyOf(keyRisk);
    }

    public KeyRisk keyRisk(String key) {
        return keyRisk.getOrDefault(key, KeyRisk.empty());
    }

    public SymbolExposure symbol(long instrumentId) {
        return symbols.get(instrumentId);
    }

    /** Existing same-symbol exposure including pending orders and active reservations. */
    public record SymbolExposure(
            long instrumentId,
            Long sectorId,
            String sectorCode,
            long signedQuantity,
            BigDecimal grossNotional,
            BigDecimal openRisk,
            BigDecimal stressRisk) {
    }

    /** Consumed capacity for one strategy/symbol/sector key (realized + open + reserved). */
    public record KeyRisk(
            BigDecimal realizedLoss,
            BigDecimal openRisk,
            BigDecimal stressRisk,
            BigDecimal grossNotional,
            BigDecimal netNotional,
            BigDecimal reservedRisk,
            BigDecimal reservedNotional,
            long tradeCount) {

        public static KeyRisk empty() {
            return new KeyRisk(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
    }

    /** Canonical NSE session window. */
    public record SessionWindow(
            Instant now,
            boolean tradingDay,
            boolean entryWindowOpen,
            boolean openingBlackout,
            boolean entryCutoffReached,
            boolean flattenWindow,
            String calendarVersion) {
    }

    /**
     * Assembly helper for inputs. It defaults to a fully unavailable context (fail-closed); callers
     * must explicitly populate the authoritative values they have.
     */
    public static Builder builder(String contextKey, long contextVersion, Instant capturedAt, LocalDate tradingDate) {
        return new Builder(contextKey, contextVersion, capturedAt, tradingDate);
    }

    public static final class Builder {
        private final String contextKey;
        private final long contextVersion;
        private final Instant capturedAt;
        private final LocalDate tradingDate;
        private boolean accountAvailable;
        private boolean portfolioAvailable;
        private boolean brokerAvailable;
        private BigDecimal riskReferenceEquity;
        private BigDecimal currentNetLiquidationValue;
        private BigDecimal availableCash;
        private BigDecimal buyingPower;
        private BigDecimal marginUsed;
        private BigDecimal grossExposure = BigDecimal.ZERO;
        private BigDecimal netExposure = BigDecimal.ZERO;
        private BigDecimal portfolioOpenRisk = BigDecimal.ZERO;
        private BigDecimal portfolioStressRisk = BigDecimal.ZERO;
        private BigDecimal reservedRisk = BigDecimal.ZERO;
        private BigDecimal reservedNotional = BigDecimal.ZERO;
        private BigDecimal sessionRealizedLoss = BigDecimal.ZERO;
        private BigDecimal sessionDrawdown = BigDecimal.ZERO;
        private BigDecimal accountDrawdown = BigDecimal.ZERO;
        private BigDecimal weeklyDrawdown = BigDecimal.ZERO;
        private BigDecimal monthlyDrawdown = BigDecimal.ZERO;
        private RiskState riskState = RiskState.HALTED;
        private long openPositionCount;
        private int consecutiveLosses;
        private String brokerHealth = "UNKNOWN";
        private String dataHealth = "UNKNOWN";
        private String reconciliationState = "UNKNOWN";
        private boolean orderStateKnown;
        private SessionWindow session;
        private Map<Long, SymbolExposure> symbols = Map.of();
        private Map<String, KeyRisk> keyRisk = Map.of();

        private Builder(String contextKey, long contextVersion, Instant capturedAt, LocalDate tradingDate) {
            this.contextKey = contextKey;
            this.contextVersion = contextVersion;
            this.capturedAt = capturedAt;
            this.tradingDate = tradingDate;
        }

        public Builder available() {
            this.accountAvailable = true;
            this.portfolioAvailable = true;
            this.brokerAvailable = true;
            this.orderStateKnown = true;
            this.brokerHealth = "HEALTHY";
            this.dataHealth = "HEALTHY";
            this.reconciliationState = "MATCHED";
            return this;
        }

        public Builder equity(BigDecimal rre, BigDecimal current) {
            this.riskReferenceEquity = rre;
            this.currentNetLiquidationValue = current;
            return this;
        }

        public Builder funding(BigDecimal cash, BigDecimal buyingPower, BigDecimal marginUsed) {
            this.availableCash = cash;
            this.buyingPower = buyingPower;
            this.marginUsed = marginUsed;
            return this;
        }

        public Builder exposures(BigDecimal gross, BigDecimal net) {
            this.grossExposure = gross;
            this.netExposure = net;
            return this;
        }

        public Builder risk(BigDecimal openRisk, BigDecimal stressOpenRisk, BigDecimal reservedRisk, BigDecimal reservedNotional) {
            this.portfolioOpenRisk = openRisk;
            this.portfolioStressRisk = stressOpenRisk;
            this.reservedRisk = reservedRisk;
            this.reservedNotional = reservedNotional;
            return this;
        }

        public Builder losses(BigDecimal sessionRealizedLoss, BigDecimal sessionDrawdown, BigDecimal weekly, BigDecimal monthly, BigDecimal account) {
            this.sessionRealizedLoss = sessionRealizedLoss;
            this.sessionDrawdown = sessionDrawdown;
            this.weeklyDrawdown = weekly;
            this.monthlyDrawdown = monthly;
            this.accountDrawdown = account;
            return this;
        }

        public Builder state(RiskState state) {
            this.riskState = state;
            return this;
        }

        public Builder counters(long openPositions, int consecutiveLosses) {
            this.openPositionCount = openPositions;
            this.consecutiveLosses = consecutiveLosses;
            return this;
        }

        public Builder health(String broker, String data, String reconciliation, boolean orderStateKnown) {
            this.brokerHealth = broker;
            this.dataHealth = data;
            this.reconciliationState = reconciliation;
            this.orderStateKnown = orderStateKnown;
            return this;
        }

        public Builder session(SessionWindow session) {
            this.session = session;
            return this;
        }

        public Builder symbols(Map<Long, SymbolExposure> symbols) {
            this.symbols = symbols;
            return this;
        }

        public Builder keyRisk(Map<String, KeyRisk> keyRisk) {
            this.keyRisk = keyRisk;
            return this;
        }

        public RiskContext build() {
            return new RiskContext(
                    contextKey, contextVersion, capturedAt, tradingDate,
                    accountAvailable, portfolioAvailable, brokerAvailable,
                    riskReferenceEquity, currentNetLiquidationValue, availableCash, buyingPower, marginUsed,
                    grossExposure, netExposure, portfolioOpenRisk, portfolioStressRisk,
                    reservedRisk, reservedNotional, sessionRealizedLoss, sessionDrawdown,
                    accountDrawdown, weeklyDrawdown, monthlyDrawdown, riskState,
                    openPositionCount, consecutiveLosses, brokerHealth, dataHealth, reconciliationState,
                    orderStateKnown, session, symbols, keyRisk);
        }
    }

    /** Convenience factory used by tests and fixtures. */
    public static RiskContext unavailable(String key, Instant at) {
        return new RiskContext(
                key, 1, at, null, false, false, false,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                RiskState.HALTED, 0, 0, "UNKNOWN", "UNKNOWN", "UNKNOWN", false,
                null, Map.of(), Map.of());
    }
}
