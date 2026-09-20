package com.edgerelative.broker.api.model;

import java.util.Set;

/**
 * Broker account profile and enabled capabilities.
 */
public record BrokerUserProfile(
        String userId,
        String uniqueClientCode,
        boolean nseEnabled,
        boolean bseEnabled,
        boolean ddpiEnabled,
        Set<BrokerSegment> activeSegments) {

    public BrokerUserProfile {
        activeSegments = Set.copyOf(activeSegments);
    }
}
