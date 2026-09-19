package com.edgerelative.broker.groww.mapper;

import com.edgerelative.broker.api.error.BrokerProtocolException;
import com.edgerelative.broker.api.model.BrokerCandle;
import com.edgerelative.broker.api.model.BrokerContract;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerExpiry;
import com.edgerelative.broker.api.model.BrokerHolding;
import com.edgerelative.broker.api.model.BrokerInstrumentType;
import com.edgerelative.broker.api.model.BrokerMargin;
import com.edgerelative.broker.api.model.BrokerMarginRequirement;
import com.edgerelative.broker.api.model.BrokerOhlc;
import com.edgerelative.broker.api.model.BrokerOptionChainEntry;
import com.edgerelative.broker.api.model.BrokerOptionChainStrike;
import com.edgerelative.broker.api.model.BrokerOptionGreeks;
import com.edgerelative.broker.api.model.BrokerOrder;
import com.edgerelative.broker.api.model.BrokerOrderStatus;
import com.edgerelative.broker.api.model.BrokerOrderType;
import com.edgerelative.broker.api.model.BrokerPosition;
import com.edgerelative.broker.api.model.BrokerProduct;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrder;
import com.edgerelative.broker.api.model.BrokerSmartOrderStatus;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.BrokerTrade;
import com.edgerelative.broker.api.model.BrokerTransactionType;
import com.edgerelative.broker.api.model.BrokerUserProfile;
import com.edgerelative.broker.api.model.BrokerValidity;
import com.edgerelative.broker.groww.dto.response.GrowwGreeksResponse;
import com.edgerelative.broker.groww.dto.response.GrowwHoldingResponse;
import com.edgerelative.broker.groww.dto.response.GrowwMarginRequirementResponse;
import com.edgerelative.broker.groww.dto.response.GrowwMarginResponse;
import com.edgerelative.broker.groww.dto.response.GrowwOptionChainResponse;
import com.edgerelative.broker.groww.dto.response.GrowwOrderResponse;
import com.edgerelative.broker.groww.dto.response.GrowwPositionResponse;
import com.edgerelative.broker.groww.dto.response.GrowwSmartOrderResponse;
import com.edgerelative.broker.groww.dto.response.GrowwTradeResponse;
import com.edgerelative.broker.groww.dto.response.GrowwUserProfileResponse;
import com.edgerelative.broker.groww.resilience.GrowwOperation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maps Groww wire objects to broker-neutral models.
 *
 * <p>Groww-specific parsing (including its documented inconsistencies such as stringified OHLC and
 * mixed timestamp formats) is contained here. Unknown required enum values fail observably; order
 * status degrades to {@code UNKNOWN} because it is genuinely open-ended.
 */
public class GrowwMapper {

    private static final DateTimeFormatter DATE_TIME_SPACE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Groww returns zone-less timestamps as exchange-local wall time (IST). Interpreting them as UTC
     * shifts every candle by +05:30, so zone-less values are resolved in {@code Asia/Kolkata}.
     */
    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("Asia/Kolkata");

    private final JsonMapper mapper;

    public GrowwMapper(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T dto(JsonNode payload, Class<T> type, GrowwOperation operation) {
        try {
            return mapper.treeToValue(payload, type);
        } catch (RuntimeException e) {
            throw new BrokerProtocolException(
                    "Groww payload did not match " + type.getSimpleName(), "groww", operation.name(), null, e);
        }
    }

    public BrokerOrder toOrder(GrowwOrderResponse dto) {
        if (dto == null) {
            return null;
        }
        return new BrokerOrder(
                dto.growwOrderId(),
                dto.orderReferenceId(),
                dto.tradingSymbol(),
                exchange(dto.exchange()),
                segment(dto.segment()),
                transactionType(dto.transactionType()),
                orderType(dto.orderType()),
                product(dto.product()),
                validity(dto.validity()),
                orderStatus(dto.orderStatus()),
                orZero(dto.quantity()),
                decimal(dto.price()),
                decimal(dto.triggerPrice()),
                orZero(dto.filledQuantity()),
                orZero(dto.remainingQuantity()),
                decimal(dto.averageFillPrice()),
                orZero(dto.deliverableQuantity()),
                dto.remark(),
                instant(dto.createdAt()),
                instant(dto.exchangeTime()),
                instant(dto.tradeDate()));
    }

    public BrokerTrade toTrade(GrowwTradeResponse dto) {
        return new BrokerTrade(
                dto.growwTradeId(),
                dto.exchangeTradeId(),
                dto.exchangeOrderId(),
                dto.growwOrderId(),
                dto.tradingSymbol(),
                dto.isin(),
                exchange(dto.exchange()),
                segment(dto.segment()),
                product(dto.product()),
                transactionType(dto.transactionType()),
                decimal(dto.price()),
                orZero(dto.quantity()),
                dto.tradeStatus(),
                dto.settlementNumber(),
                dto.remark(),
                instant(dto.createdAt()),
                instant(dto.tradeDateTime()));
    }

    public BrokerPosition toPosition(GrowwPositionResponse dto) {
        return new BrokerPosition(
                dto.tradingSymbol(),
                dto.symbolIsin(),
                exchange(dto.exchange()),
                segment(dto.segment()),
                product(dto.product()),
                orZero(dto.quantity()),
                decimal(dto.netPrice()),
                orZero(dto.creditQuantity()),
                decimal(dto.creditPrice()),
                orZero(dto.debitQuantity()),
                decimal(dto.debitPrice()),
                orZero(dto.carryForwardCreditQuantity()),
                decimal(dto.carryForwardCreditPrice()),
                orZero(dto.carryForwardDebitQuantity()),
                decimal(dto.carryForwardDebitPrice()),
                orZero(dto.netCarryForwardQuantity()),
                decimal(dto.netCarryForwardPrice()),
                decimal(dto.realisedPnl()));
    }

    public BrokerHolding toHolding(GrowwHoldingResponse dto) {
        return new BrokerHolding(
                dto.isin(),
                dto.tradingSymbol(),
                orZero(dto.quantity()),
                decimal(dto.averagePrice()),
                decimal(dto.pledgeQuantity()),
                decimal(dto.dematLockedQuantity()),
                decimal(dto.growwLockedQuantity()),
                decimal(dto.repledgeQuantity()),
                decimal(dto.t1Quantity()),
                decimal(dto.dematFreeQuantity()),
                orZero(dto.corporateActionAdditionalQuantity()),
                orZero(dto.activeDematTransferQuantity()));
    }

    public BrokerMargin toMargin(GrowwMarginResponse dto) {
        BigDecimal zero = BigDecimal.ZERO;
        return new BrokerMargin(
                decimal(dto.clearCash()),
                decimal(dto.netMarginUsed()),
                decimal(dto.brokerageAndCharges()),
                decimal(dto.collateralUsed()),
                decimal(dto.collateralAvailable()),
                decimal(dto.adhocMargin()),
                dto.fnoMarginDetails() == null
                        ? new BrokerMargin.FnoMarginDetails(zero, zero, zero, zero, zero, zero)
                        : new BrokerMargin.FnoMarginDetails(
                                decimal(dto.fnoMarginDetails().netFnoMarginUsed()),
                                decimal(dto.fnoMarginDetails().spanMarginUsed()),
                                decimal(dto.fnoMarginDetails().exposureMarginUsed()),
                                decimal(dto.fnoMarginDetails().futureBalanceAvailable()),
                                decimal(dto.fnoMarginDetails().optionBuyBalanceAvailable()),
                                decimal(dto.fnoMarginDetails().optionSellBalanceAvailable())),
                dto.equityMarginDetails() == null
                        ? new BrokerMargin.EquityMarginDetails(zero, zero, zero, zero, zero)
                        : new BrokerMargin.EquityMarginDetails(
                                decimal(dto.equityMarginDetails().netEquityMarginUsed()),
                                decimal(dto.equityMarginDetails().cncMarginUsed()),
                                decimal(dto.equityMarginDetails().misMarginUsed()),
                                decimal(dto.equityMarginDetails().cncBalanceAvailable()),
                                decimal(dto.equityMarginDetails().misBalanceAvailable())));
    }

    public BrokerMarginRequirement toMarginRequirement(GrowwMarginRequirementResponse dto) {
        return new BrokerMarginRequirement(
                decimal(dto.exposureRequired()),
                decimal(dto.spanRequired()),
                decimal(dto.optionBuyPremium()),
                decimal(dto.brokerageAndCharges()),
                decimal(dto.totalRequirement()),
                decimal(dto.cashCncMarginRequired()),
                decimal(dto.cashMisMarginRequired()),
                decimal(dto.physicalDeliveryMarginRequirement()));
    }

    public BrokerUserProfile toUserProfile(GrowwUserProfileResponse dto) {
        Set<BrokerSegment> segments = new java.util.LinkedHashSet<>();
        if (dto.activeSegments() != null) {
            for (String raw : dto.activeSegments()) {
                BrokerSegment mapped = segmentOrNull(raw);
                if (mapped != null && mapped != BrokerSegment.COMMODITY) {
                    segments.add(mapped);
                }
            }
        }
        return new BrokerUserProfile(
                dto.vendorUserId(),
                dto.ucc(),
                Boolean.TRUE.equals(dto.nseEnabled()),
                Boolean.TRUE.equals(dto.bseEnabled()),
                Boolean.TRUE.equals(dto.ddpiEnabled()),
                segments);
    }

    public BrokerOhlc toOhlc(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            return new BrokerOhlc(
                    decimal(node.path("open")),
                    decimal(node.path("high")),
                    decimal(node.path("low")),
                    decimal(node.path("close")));
        }
        String text = node.isString() ? node.asString() : node.toString();
        return new BrokerOhlc(
                extractNumber(text, "open"),
                extractNumber(text, "high"),
                extractNumber(text, "low"),
                extractNumber(text, "close"));
    }

    public BrokerOptionGreeks toGreeks(GrowwGreeksResponse.Greeks greeks) {
        if (greeks == null) {
            return null;
        }
        return new BrokerOptionGreeks(
                orZero(greeks.delta()),
                orZero(greeks.gamma()),
                orZero(greeks.theta()),
                orZero(greeks.vega()),
                orZero(greeks.rho()),
                orZero(greeks.impliedVolatility()));
    }

    public BrokerOptionChainEntry toOptionEntry(GrowwOptionChainResponse.Entry entry) {
        if (entry == null) {
            return null;
        }
        return new BrokerOptionChainEntry(
                entry.tradingSymbol(),
                decimal(entry.lastPrice()),
                orZero(entry.openInterest()),
                orZero(entry.volume()),
                toGreeks(entry.greeks()));
    }

    public List<BrokerOptionChainStrike> toStrikes(GrowwOptionChainResponse dto, GrowwOperation operation) {
        List<BrokerOptionChainStrike> strikes = new ArrayList<>();
        if (dto.strikes() == null) {
            return strikes;
        }
        for (Map.Entry<String, GrowwOptionChainResponse.Strike> e : dto.strikes().entrySet()) {
            BigDecimal strike = parseDecimalOrNull(e.getKey());
            if (strike == null) {
                throw new BrokerProtocolException(
                        "Option chain strike key was not numeric: " + e.getKey(), "groww", operation.name(), null, null);
            }
            strikes.add(new BrokerOptionChainStrike(
                    strike, toOptionEntry(e.getValue().call()), toOptionEntry(e.getValue().put())));
        }
        strikes.sort(java.util.Comparator.comparing(BrokerOptionChainStrike::strikePrice));
        return strikes;
    }

    /**
     * Maps one historical candle row, returning {@code null} when the row is unusable.
     *
     * <p>Groww occasionally emits a row with a missing/incomplete bar (null or non-numeric OHLC,
     * e.g. a halted or untraded session). A single such row must not fail the whole series; callers
     * skip nulls and decide whether the overall result is trustworthy.
     *
     * <p>Groww deliberately omits {@code open} on daily cash-equity candles (it is present for
     * indices and intraday), so {@code open} is treated as optional while high/low/close remain
     * required for a usable bar.
     */
    public BrokerCandle toCandle(List<JsonNode> row) {
        if (row == null || row.size() < 6) {
            return null;
        }
        Instant openTime = instant(row.get(0));
        BigDecimal open = decimal(row.get(1));
        BigDecimal high = decimal(row.get(2));
        BigDecimal low = decimal(row.get(3));
        BigDecimal close = decimal(row.get(4));
        if (openTime == null || high == null || low == null || close == null) {
            return null;
        }
        BigDecimal openInterest = row.size() > 6 ? decimal(row.get(6)) : null;
        return new BrokerCandle(openTime, open, high, low, close, longValue(decimal(row.get(5))), openInterest);
    }

    public BrokerExpiry toExpiry(String date) {
        return new BrokerExpiry(LocalDate.parse(date));
    }

    public BrokerContract toContract(String symbol) {
        return new BrokerContract(symbol);
    }

    public BrokerSmartOrder toSmartOrder(GrowwSmartOrderResponse dto) {
        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "order", dto.order());
        putIfPresent(details, "target", dto.target());
        putIfPresent(details, "stopLoss", dto.stopLoss());
        putIfPresent(details, "childLegs", dto.childLegs());
        putIfPresent(details, "amoStatus", null);
        return new BrokerSmartOrder(
                dto.smartOrderId(),
                smartOrderTypeOrNull(dto.smartOrderType()),
                smartOrderStatus(dto.status()),
                dto.tradingSymbol(),
                exchangeOrNull(dto.exchange()),
                segmentOrNull(dto.segment()),
                orZero(dto.quantity()),
                productOrNull(dto.productType()),
                validityOrNull(dto.duration()),
                decimal(dto.triggerPrice()),
                dto.triggerDirection(),
                decimal(dto.lastPrice()),
                Boolean.TRUE.equals(dto.cancellationAllowed()),
                Boolean.TRUE.equals(dto.modificationAllowed()),
                instant(dto.createdAt()),
                instant(dto.expireAt()),
                instant(dto.triggeredAt()),
                instant(dto.updatedAt()),
                details);
    }

    private static void putIfPresent(Map<String, Object> map, String key, JsonNode value) {
        if (value != null && !value.isNull() && !value.isMissingNode()) {
            map.put(key, value.toString());
        }
    }

    public BrokerExchange exchange(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "NSE" -> BrokerExchange.NSE;
            case "BSE" -> BrokerExchange.BSE;
            case "MCX" -> BrokerExchange.MCX;
            default -> throw new BrokerProtocolException(
                    "Unknown Groww exchange: " + raw, "groww", null, null, null);
        };
    }

    public BrokerExchange exchangeOrNull(String raw) {
        try {
            return exchange(raw);
        } catch (BrokerProtocolException e) {
            return null;
        }
    }

    public BrokerSegment segment(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "CASH" -> BrokerSegment.CASH;
            case "FNO" -> BrokerSegment.FNO;
            case "COMMODITY" -> BrokerSegment.COMMODITY;
            default -> throw new BrokerProtocolException(
                    "Unknown Groww segment: " + raw, "groww", null, null, null);
        };
    }

    public BrokerSegment segmentOrNull(String raw) {
        try {
            return segment(raw);
        } catch (BrokerProtocolException e) {
            return null;
        }
    }

    public BrokerTransactionType transactionType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "BUY" -> BrokerTransactionType.BUY;
            case "SELL" -> BrokerTransactionType.SELL;
            default -> throw new BrokerProtocolException(
                    "Unknown Groww transaction type: " + raw, "groww", null, null, null);
        };
    }

    public BrokerOrderType orderType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "LIMIT" -> BrokerOrderType.LIMIT;
            case "MARKET" -> BrokerOrderType.MARKET;
            case "SL" -> BrokerOrderType.SL;
            case "SL_M" -> BrokerOrderType.SL_M;
            default -> throw new BrokerProtocolException(
                    "Unknown Groww order type: " + raw, "groww", null, null, null);
        };
    }

    public BrokerProduct product(String raw) {
        return productOrNull(raw);
    }

    public BrokerProduct productOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "CNC" -> BrokerProduct.CNC;
            case "MIS" -> BrokerProduct.MIS;
            case "NRML" -> BrokerProduct.NRML;
            default -> null;
        };
    }

    public BrokerValidity validity(String raw) {
        return validityOrNull(raw);
    }

    public BrokerValidity validityOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "DAY" -> BrokerValidity.DAY;
            default -> null;
        };
    }

    /** Order status is open-ended, so unknown values degrade safely to {@code UNKNOWN}. */
    public BrokerOrderStatus orderStatus(String raw) {
        if (raw == null) {
            return BrokerOrderStatus.UNKNOWN;
        }
        try {
            return BrokerOrderStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BrokerOrderStatus.UNKNOWN;
        }
    }

    public BrokerSmartOrderType smartOrderTypeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.toUpperCase()) {
            case "GTT" -> BrokerSmartOrderType.GTT;
            case "OCO" -> BrokerSmartOrderType.OCO;
            default -> null;
        };
    }

    public BrokerSmartOrderStatus smartOrderStatus(String raw) {
        if (raw == null) {
            return BrokerSmartOrderStatus.UNKNOWN;
        }
        try {
            return BrokerSmartOrderStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BrokerSmartOrderStatus.UNKNOWN;
        }
    }

    public BrokerInstrumentType instrumentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return BrokerInstrumentType.OTHER;
        }
        return switch (raw.trim().toUpperCase()) {
            case "EQ" -> BrokerInstrumentType.EQ;
            case "IDX" -> BrokerInstrumentType.IDX;
            case "FUT" -> BrokerInstrumentType.FUT;
            case "CE" -> BrokerInstrumentType.CE;
            case "PE" -> BrokerInstrumentType.PE;
            default -> BrokerInstrumentType.OTHER;
        };
    }

    public static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asString(null);
        if (text == null || text.isBlank() || "NaN".equalsIgnoreCase(text)) {
            return null;
        }
        return parseDecimalOrNull(text);
    }

    static BigDecimal parseDecimalOrNull(String value) {
        if (value == null || value.isBlank() || "NaN".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Instant instant(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            long value = node.asLong();
            return value > 100_000_000_000L
                    ? Instant.ofEpochMilli(value)
                    : Instant.ofEpochSecond(value);
        }
        return parseInstant(node.asString(null));
    }

    public Instant instant(String text) {
        return parseInstant(text);
    }

    static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            // try the next documented format
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_SPACE).atZone(EXCHANGE_ZONE).toInstant();
        } catch (RuntimeException ignored) {
            // try the next documented format
        }
        try {
            return LocalDateTime.parse(value).atZone(EXCHANGE_ZONE).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static BigDecimal extractNumber(String text, String field) {
        if (text == null) {
            return null;
        }
        int idx = text.indexOf(field);
        if (idx < 0) {
            return null;
        }
        int colon = text.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = colon + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+') {
                sb.append(c);
            } else if (sb.length() > 0) {
                break;
            }
        }
        return parseDecimalOrNull(sb.toString());
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static long longValue(BigDecimal value) {
        return value == null ? 0L : value.longValue();
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    static Duration minutesToDuration(Integer minutes) {
        return minutes == null ? null : Duration.ofMinutes(minutes);
    }
}
