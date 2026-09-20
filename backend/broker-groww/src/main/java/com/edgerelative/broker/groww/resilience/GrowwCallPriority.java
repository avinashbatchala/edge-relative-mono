package com.edgerelative.broker.groww.resilience;

/**
 * Admission priority. Interactive state must not queue behind bulk historical fan-out.
 */
public enum GrowwCallPriority {
    INTERACTIVE,
    BULK
}
