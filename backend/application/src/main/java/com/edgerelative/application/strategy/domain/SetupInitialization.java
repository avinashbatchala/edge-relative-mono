package com.edgerelative.application.strategy.domain;

/**
 * How a setup instance came into existence. A normal lifecycle starts at WATCH; a reconstruction
 * that first observes an already-active state (FORMING/NEAR_TRIGGER/VALID) is a cold start. The
 * distinction matters for audit: a cold start skips historical lifecycle observations but never
 * skips qualification rules.
 */
public enum SetupInitialization {
    /** First transition NONE → WATCH. */
    LIFECYCLE_START,
    /** First observed state is already above WATCH, with no prior instance (replay/restart). */
    COLD_START_RECONSTRUCTION
}
