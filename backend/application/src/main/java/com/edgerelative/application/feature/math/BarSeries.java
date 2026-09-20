package com.edgerelative.application.feature.math;

import com.edgerelative.application.history.AggregatedCandle;

import java.time.Instant;
import java.util.List;

/**
 * Immutable columnar view of canonical candles for feature calculation.
 *
 * <p>Only causal prefixes are ever read: a calculator receives the full series but a {@code length}
 * bound, and must not look beyond it. This is the single representation shared by live, replay and
 * backtest so there is no second input path.
 */
public final class BarSeries {

    private final Instant[] openTimes;
    private final Instant[] closeTimes;
    private final double[] open;
    private final double[] high;
    private final double[] low;
    private final double[] close;
    private final long[] volume;
    private final boolean[] complete;
    private final String[] quality;

    private BarSeries(
            Instant[] openTimes,
            Instant[] closeTimes,
            double[] open,
            double[] high,
            double[] low,
            double[] close,
            long[] volume,
            boolean[] complete,
            String[] quality) {
        this.openTimes = openTimes;
        this.closeTimes = closeTimes;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.complete = complete;
        this.quality = quality;
    }

    public static BarSeries of(List<AggregatedCandle> candles) {
        int size = candles.size();
        Instant[] openTimes = new Instant[size];
        Instant[] closeTimes = new Instant[size];
        double[] open = new double[size];
        double[] high = new double[size];
        double[] low = new double[size];
        double[] close = new double[size];
        long[] volume = new long[size];
        boolean[] complete = new boolean[size];
        String[] quality = new String[size];
        for (int i = 0; i < size; i++) {
            AggregatedCandle candle = candles.get(i);
            openTimes[i] = candle.openTime();
            closeTimes[i] = candle.closeTime();
            open[i] = candle.open() == null ? Double.NaN : candle.open().doubleValue();
            high[i] = candle.high() == null ? Double.NaN : candle.high().doubleValue();
            low[i] = candle.low() == null ? Double.NaN : candle.low().doubleValue();
            close[i] = candle.close() == null ? Double.NaN : candle.close().doubleValue();
            volume[i] = candle.volume();
            complete[i] = candle.complete();
            quality[i] = candle.qualityState();
        }
        return new BarSeries(openTimes, closeTimes, open, high, low, close, volume, complete, quality);
    }

    public int size() {
        return close.length;
    }

    public Instant openTime(int index) {
        return openTimes[index];
    }

    public Instant closeTime(int index) {
        return closeTimes[index];
    }

    public double open(int index) {
        return open[index];
    }

    public double high(int index) {
        return high[index];
    }

    public double low(int index) {
        return low[index];
    }

    public double close(int index) {
        return close[index];
    }

    public long volume(int index) {
        return volume[index];
    }

    public boolean complete(int index) {
        return complete[index];
    }

    public String quality(int index) {
        return quality[index];
    }

    /**
     * Index of the last bar whose close time is at or before {@code timestamp}, or -1.
     */
    public int lastIndexAtOrBefore(Instant timestamp) {
        int result = -1;
        for (int i = 0; i < size(); i++) {
            if (!closeTimes[i].isAfter(timestamp)) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Index whose close time equals {@code timestamp}, or -1. Assumes ascending close times.
     */
    public int indexOfCloseTime(Instant timestamp) {
        for (int i = 0; i < size(); i++) {
            int comparison = closeTimes[i].compareTo(timestamp);
            if (comparison == 0) {
                return i;
            }
            if (comparison > 0) {
                return -1;
            }
        }
        return -1;
    }
}
