package com.edgerelative.application.feature.stream;

/**
 * Versioned JSON WebSocket envelope for feature updates.
 *
 * <p>{@code type} names the message, {@code version} is the envelope/contract version (independent
 * of feature semantic versions), and {@code sequence} is a monotonically increasing server counter so
 * clients can detect gaps and request an authoritative resync.
 */
public record FeatureStreamEnvelope(String type, int version, long sequence, Object payload) {

    public static final int CURRENT_VERSION = 1;
    public static final String FEATURE_SNAPSHOT = "feature.snapshot";
    public static final String FEATURE_UPDATE = "feature.update";
    public static final String RESYNC = "feature.resync";

    public static FeatureStreamEnvelope of(String type, long sequence, Object payload) {
        return new FeatureStreamEnvelope(type, CURRENT_VERSION, sequence, payload);
    }
}
