package com.edgerelative.application.broker.api;

/**
 * Stable application error body. Never carries credentials, tokens or authorization headers.
 *
 * @param code stable machine code, e.g. {@code BROKER_OPERATION_NOT_ENABLED}
 * @param message human-readable, broker message where safe
 * @param operation broker operation, when known
 */
public record ApiError(String code, String message, String operation) {
}
