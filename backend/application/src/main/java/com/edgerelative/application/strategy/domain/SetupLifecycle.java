package com.edgerelative.application.strategy.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Explicit allowed setup-state transitions (DD-02 §74/§16). Illegal transitions are rejected. */
public final class SetupLifecycle {

    private static final Map<SetupState, Set<SetupState>> ALLOWED = Map.of(
            SetupState.NONE, EnumSet.of(SetupState.WATCH),
            SetupState.WATCH, EnumSet.of(SetupState.FORMING, SetupState.BLOCKED, SetupState.INVALIDATED, SetupState.EXPIRED),
            SetupState.FORMING, EnumSet.of(SetupState.NEAR_TRIGGER, SetupState.BLOCKED, SetupState.INVALIDATED, SetupState.EXPIRED),
            SetupState.NEAR_TRIGGER, EnumSet.of(SetupState.VALID, SetupState.BLOCKED, SetupState.INVALIDATED, SetupState.EXPIRED, SetupState.MISSED),
            SetupState.VALID, EnumSet.of(SetupState.INVALIDATED, SetupState.EXPIRED, SetupState.MISSED),
            SetupState.INVALIDATED, EnumSet.noneOf(SetupState.class),
            SetupState.EXPIRED, EnumSet.noneOf(SetupState.class),
            SetupState.MISSED, EnumSet.noneOf(SetupState.class),
            SetupState.BLOCKED, EnumSet.noneOf(SetupState.class));

    private SetupLifecycle() {
    }

    /** A repeated identical evaluation is not a transition and is always permitted. */
    public static boolean allowed(SetupState from, SetupState to) {
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireValid(SetupState from, SetupState to) {
        if (!allowed(from, to)) {
            throw new IllegalStateException(
                    "Illegal setup transition " + from + " -> " + to + " (" + ReasonCode.ILLEGAL_STATE_TRANSITION + ")");
        }
    }

    /** A reconstruction that starts mid-session may bootstrap directly into a non-terminal state. */
    public static boolean bootstrapAllowed(SetupState to) {
        return !to.isTerminal();
    }
}
