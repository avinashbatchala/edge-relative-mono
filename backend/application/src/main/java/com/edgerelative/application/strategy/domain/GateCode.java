package com.edgerelative.application.strategy.domain;

/**
 * Hard gates. A failed or unavailable gate prevents {@code VALID}. Quality factors are modelled
 * separately and never silently become gates (DD-02 §8).
 */
public enum GateCode {
    DATA_VALIDITY,
    SESSION_ENTRY_ALLOWED,
    MARKET_BIAS_PERMISSION,
    MARKET_REGIME_PERMISSION,
    DAILY_STRUCTURE_ALIGNED,
    RRS_D1_DIRECTION,
    RRS_M5_PERSISTENCE,
    VOLUME_PARTICIPATION,
    LIQUIDITY,
    TECHNICAL_VOID,
    EVENT_RISK,
    SETUP_STRUCTURE,
    TRIGGER_CONFIRMED,
    ENTRY_EXTENSION,
    STRUCTURAL_INVALIDATION
}
