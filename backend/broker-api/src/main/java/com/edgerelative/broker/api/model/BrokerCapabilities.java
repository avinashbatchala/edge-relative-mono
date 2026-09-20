package com.edgerelative.broker.api.model;

import java.util.Set;

/**
 * Capability answer for a specific broker adapter, so callers never use {@code instanceof}.
 */
public record BrokerCapabilities(String broker, Set<BrokerCapability> capabilities) {

    public BrokerCapabilities {
        capabilities = Set.copyOf(capabilities);
    }

    public boolean supports(BrokerCapability capability) {
        return capabilities.contains(capability);
    }
}
