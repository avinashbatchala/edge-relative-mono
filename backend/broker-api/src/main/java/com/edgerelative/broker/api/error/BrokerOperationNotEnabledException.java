package com.edgerelative.broker.api.error;

/**
 * Raised by the safe default adapter for every broker-side mutation.
 *
 * <p>Edge Relative records order intent and exposes application contracts, but execution authority is
 * deliberately disabled. This exception is the only thing a mutation path may do: no downstream HTTP
 * request may be emitted.
 */
public final class BrokerOperationNotEnabledException extends BrokerException {

    public static final String CODE = "BROKER_OPERATION_NOT_ENABLED";

    private final String brokerName;

    public BrokerOperationNotEnabledException(String brokerName, String operation) {
        super(
                "Broker operation '%s' is not enabled for brokers other than a verified execution authority"
                        .formatted(operation),
                brokerName,
                operation,
                null,
                CODE,
                null,
                false,
                null);
        this.brokerName = brokerName;
    }

    public String brokerName() {
        return brokerName;
    }
}
