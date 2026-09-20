package com.edgerelative.application.feature.domain;

/**
 * Canonical feature identity codes. Codes are broker-neutral and stable; versioning lives in
 * {@link FeatureVersion}. Magic feature-name strings are prohibited outside this class and the
 * registry (DD-04 clean-code rules).
 */
public final class FeatureKeys {

    private FeatureKeys() {
    }

    // Volatility
    public static final String ATR = "ATR";

    // Relative strength (stock vs broad market)
    public static final String RRS_RAW = "RRS_RAW";
    public static final String RRS_FAST = "RRS_FAST";
    public static final String RRS_SLOW = "RRS_SLOW";
    public static final String RRS_PERSISTENCE = "RRS_PERSISTENCE";
    public static final String RRS_SLOPE = "RRS_SLOPE";
    public static final String RRS_ACCELERATION = "RRS_ACCELERATION";
    public static final String RRS_PERCENTILE = "RRS_PERCENTILE";
    public static final String RRS_TREND_STATE = "RRS_TREND_STATE";

    // Relative strength (stock vs sector, sector vs broad market)
    public static final String RRS_VS_SECTOR_RAW = "RRS_VS_SECTOR_RAW";
    public static final String SECTOR_RRS_RAW = "SECTOR_RRS_RAW";

    // Volume
    public static final String RVOL_D1 = "RVOL_D1";
    public static final String RVOL_INTERVAL = "RVOL_INTERVAL";
    public static final String RVOL_CUMULATIVE = "RVOL_CUMULATIVE";
    public static final String RVE = "RVE";
    public static final String DIRECTIONAL_VOLUME_LONG = "DIRECTIONAL_VOLUME_LONG";
    public static final String DIRECTIONAL_VOLUME_SHORT = "DIRECTIONAL_VOLUME_SHORT";

    // Market context
    public static final String MARKET_ATR = "MARKET_ATR";
    public static final String MARKET_DIRECTIONAL_EFFICIENCY = "MARKET_DIRECTIONAL_EFFICIENCY";
    public static final String MARKET_PRICE_STRUCTURE = "MARKET_PRICE_STRUCTURE";

    // Sector context
    public static final String SECTOR_DIRECTIONAL_EFFICIENCY = "SECTOR_DIRECTIONAL_EFFICIENCY";
    public static final String SECTOR_PRICE_STRUCTURE = "SECTOR_PRICE_STRUCTURE";
}
