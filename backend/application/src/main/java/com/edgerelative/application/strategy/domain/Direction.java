package com.edgerelative.application.strategy.domain;

/** Directional intent. Long and short are evaluated symmetrically but with explicit rules. */
public enum Direction {
    LONG,
    SHORT;

    public boolean isLong() {
        return this == LONG;
    }
}
