package com.edgerelative.application.strategy.domain;

/**
 * Quality factors. Recorded as explicit checkboxes, never collapsed into an opaque weighted score
 * in V1 (DD-02 §8). A factor never silently becomes a hard gate.
 */
public enum QualityFactor {
    STACKED_SECTOR_STRENGTH,
    MULTI_TIMEFRAME_RRS_AGREEMENT,
    RISING_RRS,
    RRS_ACCELERATION,
    ELEVATED_RVOL,
    FAVORABLE_RVE,
    DAILY_MA_STACK,
    HEIKIN_ASHI_CONFIRMATION,
    DIRECTIONAL_VOLUME_ALIGNED,
    PREVIOUS_DAY_LEVEL_BREAK,
    CLEAN_COMPRESSION,
    SECTOR_ALIGNMENT,
    TRIGGER_QUALITY,
    LIQUIDITY_QUALITY
}
