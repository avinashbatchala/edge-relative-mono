package com.edgerelative.broker.groww.resilience;

import com.edgerelative.broker.groww.config.GrowwProperties;
import java.time.Duration;
import java.util.List;

/** Bounded-cardinality operation tags for metrics, rate-limit selection and retry classification. */
public enum GrowwOperation {
    INSTRUMENT_MASTER(GrowwRateLimitCategory.NON_TRADING, true),
    QUOTE(GrowwRateLimitCategory.LIVE_DATA, true),
    LTP(GrowwRateLimitCategory.LIVE_DATA, true),
    OHLC(GrowwRateLimitCategory.LIVE_DATA, true),
    OPTION_CHAIN(GrowwRateLimitCategory.LIVE_DATA, true),
    GREEKS(GrowwRateLimitCategory.LIVE_DATA, true),
    HISTORICAL_CANDLES(GrowwRateLimitCategory.NON_TRADING, true),
    HISTORICAL_EXPIRIES(GrowwRateLimitCategory.NON_TRADING, true),
    HISTORICAL_CONTRACTS(GrowwRateLimitCategory.NON_TRADING, true),
    HOLDINGS(GrowwRateLimitCategory.NON_TRADING, true),
    POSITIONS(GrowwRateLimitCategory.NON_TRADING, true),
    POSITION_BY_SYMBOL(GrowwRateLimitCategory.NON_TRADING, true),
    USER_PROFILE(GrowwRateLimitCategory.NON_TRADING, true),
    ORDER_STATUS(GrowwRateLimitCategory.NON_TRADING, true),
    ORDER_STATUS_BY_REFERENCE(GrowwRateLimitCategory.NON_TRADING, true),
    ORDER_DETAIL(GrowwRateLimitCategory.NON_TRADING, true),
    ORDER_LIST(GrowwRateLimitCategory.NON_TRADING, true),
    ORDER_TRADES(GrowwRateLimitCategory.NON_TRADING, true),
    USER_MARGIN(GrowwRateLimitCategory.NON_TRADING, true),
    REQUIRED_MARGIN(GrowwRateLimitCategory.NON_TRADING, true),
    SMART_ORDER_GET(GrowwRateLimitCategory.NON_TRADING, true),
    SMART_ORDER_LIST(GrowwRateLimitCategory.NON_TRADING, true),
    TOKEN_GENERATE(GrowwRateLimitCategory.AUTHENTICATION, false),
    PLACE_ORDER(GrowwRateLimitCategory.ORDERS, false),
    MODIFY_ORDER(GrowwRateLimitCategory.ORDERS, false),
    CANCEL_ORDER(GrowwRateLimitCategory.ORDERS, false),
    SMART_ORDER_CREATE(GrowwRateLimitCategory.ORDERS, false),
    SMART_ORDER_MODIFY(GrowwRateLimitCategory.ORDERS, false),
    SMART_ORDER_CANCEL(GrowwRateLimitCategory.ORDERS, false);

    private final GrowwRateLimitCategory category;
    private final boolean retrySafe;

    GrowwOperation(GrowwRateLimitCategory category, boolean retrySafe) {
        this.category = category;
        this.retrySafe = retrySafe;
    }

    public GrowwRateLimitCategory category() {
        return category;
    }

    /** True only for read-only/idempotent operations that may be replayed on transient failure. */
    public boolean retrySafe() {
        return retrySafe;
    }

    public static List<GrowwProperties.Window> windowsFor(
            GrowwProperties properties, GrowwRateLimitCategory category) {
        GrowwProperties.RateLimits limits = properties.getRateLimits();
        return switch (category) {
            case AUTHENTICATION -> limits.getAuthentication();
            case ORDERS -> limits.getOrders();
            case LIVE_DATA -> limits.getLiveData();
            case NON_TRADING -> limits.getNonTrading();
        };
    }

    /** The bound the limiter may block for before returning a typed rate-limit failure. */
    public static Duration maxWaitFor(GrowwProperties properties) {
        return properties.getOperationTimeout();
    }
}
