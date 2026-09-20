package com.edgerelative.application.broker.api;

import com.edgerelative.broker.api.error.BrokerValidationException;
import com.edgerelative.broker.api.model.BrokerExchange;
import com.edgerelative.broker.api.model.BrokerOrderType;
import com.edgerelative.broker.api.model.BrokerProduct;
import com.edgerelative.broker.api.model.BrokerSegment;
import com.edgerelative.broker.api.model.BrokerSmartOrderType;
import com.edgerelative.broker.api.model.BrokerTransactionType;
import com.edgerelative.broker.api.model.BrokerValidity;

/**
 * Case-insensitive conversion of application strings to broker-neutral enums.
 */
public final class BrokerApiEnums {

    private BrokerApiEnums() {
    }

    public static BrokerExchange exchange(String value) {
        return parse(BrokerExchange.class, value);
    }

    public static BrokerSegment segment(String value) {
        return parse(BrokerSegment.class, value);
    }

    public static BrokerOrderType orderType(String value) {
        return parse(BrokerOrderType.class, value);
    }

    public static BrokerProduct product(String value) {
        return parse(BrokerProduct.class, value);
    }

    public static BrokerTransactionType transactionType(String value) {
        return parse(BrokerTransactionType.class, value);
    }

    public static BrokerValidity validity(String value) {
        return parse(BrokerValidity.class, value);
    }

    public static BrokerSmartOrderType smartOrderType(String value) {
        return parse(BrokerSmartOrderType.class, value);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new BrokerValidationException(
                    type.getSimpleName() + " is required", "groww", null, null, "VALIDATION", 400);
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BrokerValidationException(
                    "Unknown " + type.getSimpleName() + ": " + value, "groww", null, null, "VALIDATION", 400);
        }
    }
}
