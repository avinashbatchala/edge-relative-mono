package com.edgerelative.application.risk.domain;

/** Unit of a constraint value. Currency risk budgets are never mixed with currency notionals. */
public enum ConstraintKind {
    CURRENCY,
    QUANTITY,
    RATIO,
    COUNT
}
