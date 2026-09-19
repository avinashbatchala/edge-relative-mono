/**
 * Broker-neutral trading integration contracts.
 *
 * <p>Nothing in this package may reference a specific broker. Adapters such as {@code broker-groww}
 * implement these ports. Trading, strategy, risk and feature code depends on this module, never on a
 * broker adapter.
 */
package com.edgerelative.broker.api;
