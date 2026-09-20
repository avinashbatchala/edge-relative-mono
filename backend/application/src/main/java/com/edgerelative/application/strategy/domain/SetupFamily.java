package com.edgerelative.application.strategy.domain;

/**
 * Strategy V1 setup families (DD-02 §73). Each family is an explicit structure + trigger + family
 * specific invalidation, not an interchangeable label.
 */
public enum SetupFamily {
    M5_COMPRESSION_BREAKOUT,
    M5_TRENDLINE_BREAK,
    M5_3_8_CONFIRMATION,
    M5_HORIZONTAL_LEVEL_BREAK,
    M5_PULLBACK_RESUMPTION;

    public static SetupFamily parse(String value) {
        return SetupFamily.valueOf(value.trim().toUpperCase());
    }
}
