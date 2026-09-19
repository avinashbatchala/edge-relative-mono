package com.edgerelative.broker.api.model;

import java.time.Duration;

/**
 * Supported historical candle intervals.
 *
 * <p>These are the broker's named intervals, kept separate from Edge Relative's canonical timeframe
 * registry so broker naming never becomes domain identity.
 */
public enum BrokerCandleInterval {
    ONE_MINUTE("1minute", Duration.ofMinutes(1)),
    TWO_MINUTE("2minute", Duration.ofMinutes(2)),
    THREE_MINUTE("3minute", Duration.ofMinutes(3)),
    FIVE_MINUTE("5minute", Duration.ofMinutes(5)),
    TEN_MINUTE("10minute", Duration.ofMinutes(10)),
    FIFTEEN_MINUTE("15minute", Duration.ofMinutes(15)),
    THIRTY_MINUTE("30minute", Duration.ofMinutes(30)),
    ONE_HOUR("1hour", Duration.ofHours(1)),
    FOUR_HOUR("4hour", Duration.ofHours(4)),
    ONE_DAY("1day", Duration.ofDays(1)),
    ONE_WEEK("1week", Duration.ofDays(7)),
    ONE_MONTH("1month", Duration.ofDays(30));

    private final String wireValue;
    private final Duration duration;

    BrokerCandleInterval(String wireValue, Duration duration) {
        this.wireValue = wireValue;
        this.duration = duration;
    }

    public String wireValue() {
        return wireValue;
    }

    public Duration duration() {
        return duration;
    }
}
