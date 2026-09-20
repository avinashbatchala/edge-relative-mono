package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FeatureEngineTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final FeatureEngine ENGINE = new FeatureEngine();

    private static List<AggregatedCandle> sessions(LocalDate first, LocalDate second, long volume) {
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.addAll(FeatureTestSupport.session(first, volume));
        bars.addAll(FeatureTestSupport.session(second, volume));
        return bars;
    }

    @Test
    void snapshotCarriesMarketSectorAndStockRelativeFeatures() {
        List<AggregatedCandle> subject = sessions(D1, D2, 100);
        List<AggregatedCandle> market = sessions(D1, D2, 500);
        List<AggregatedCandle> sector = sessions(D1, D2, 300);
        FeatureSnapshot snapshot = ENGINE.snapshot(
                FeatureTestSupport.context(1, subject, market, sector, FeatureTestSupport.policy(), "M5"));

        assertThat(snapshot.market()).isNotNull();
        assertThat(snapshot.sector()).isNotNull();
        assertThat(snapshot.feature(FeatureKeys.ATR)).isNotNull();
        assertThat(snapshot.feature(FeatureKeys.RRS_RAW)).isNotNull();
        assertThat(snapshot.sector().features().get(FeatureKeys.SECTOR_RRS_RAW)).isNotNull();
        assertThat(snapshot.feature(FeatureKeys.RRS_VS_SECTOR_RAW)).isNotNull();
        assertThat(snapshot.market().features().get(FeatureKeys.MARKET_ATR)).isNotNull();
    }

    @Test
    void staleMarketBarDegradesStockRrsQuality() {
        List<AggregatedCandle> subject = sessions(D1, D2, 100);
        List<AggregatedCandle> staleMarket = new ArrayList<>();
        for (AggregatedCandle candle : sessions(D1, D2, 500)) {
            staleMarket.add(FeatureTestSupport.bar(
                    candle.openTime().toString(),
                    candle.closeTime().toString(),
                    candle.open().doubleValue(),
                    candle.high().doubleValue(),
                    candle.low().doubleValue(),
                    candle.close().doubleValue(),
                    candle.volume(),
                    true,
                    "STALE"));
        }
        FeatureSnapshot snapshot = ENGINE.snapshot(
                FeatureTestSupport.context(1, subject, staleMarket, List.of(), FeatureTestSupport.policy(), "M5"));
        assertThat(snapshot.feature(FeatureKeys.RRS_RAW).quality()).isEqualTo(FeatureQuality.STALE);
        assertThat(snapshot.quality().trustworthy()).isFalse();
    }

    @Test
    void incompleteSubjectBarBlocksFinalizedFeatures() {
        List<AggregatedCandle> subject = new ArrayList<>(sessions(D1, D2, 100));
        int last = subject.size() - 1;
        AggregatedCandle incomplete = subject.get(last);
        subject.set(last, FeatureTestSupport.bar(
                incomplete.openTime().toString(),
                incomplete.closeTime().toString(),
                incomplete.open().doubleValue(),
                incomplete.high().doubleValue(),
                incomplete.low().doubleValue(),
                incomplete.close().doubleValue(),
                incomplete.volume(),
                false,
                "INCOMPLETE"));
        FeatureSnapshot snapshot = ENGINE.snapshot(
                FeatureTestSupport.context(1, subject, sessions(D1, D2, 500), List.of(), FeatureTestSupport.policy(), "M5"));
        assertThat(snapshot.feature(FeatureKeys.RRS_RAW).availability()).isEqualTo(FeatureAvailability.INCOMPLETE);
        assertThat(snapshot.availability()).isEqualTo(FeatureAvailability.INCOMPLETE);
    }

    @Test
    void missingBenchmarkLeavesMarketContextNullAndRrsUnavailable() {
        FeatureSnapshot snapshot = ENGINE.snapshot(FeatureTestSupport.context(
                1, sessions(D1, D2, 100), List.of(), List.of(), FeatureTestSupport.policy(), "M5"));
        assertThat(snapshot.market()).isNull();
        assertThat(snapshot.feature(FeatureKeys.RRS_RAW).availability()).isNotEqualTo(FeatureAvailability.VALID);
    }

    @Test
    void directionalVolumeRatiosSeparateUpAndDownVolume() {
        List<AggregatedCandle> bars = new ArrayList<>();
        bars.add(FeatureTestSupport.bar("2026-09-01T03:45:00Z", "2026-09-01T03:50:00Z", 100, 101, 99, 101, 30));
        bars.add(FeatureTestSupport.bar("2026-09-01T03:50:00Z", "2026-09-01T03:55:00Z", 101, 102, 100, 102, 30));
        bars.add(FeatureTestSupport.bar("2026-09-01T03:55:00Z", "2026-09-01T04:00:00Z", 102, 103, 101, 103, 30));
        bars.add(FeatureTestSupport.bar("2026-09-01T04:00:00Z", "2026-09-01T04:05:00Z", 103, 104, 101, 101, 10));
        bars.add(FeatureTestSupport.bar("2026-09-01T04:05:00Z", "2026-09-01T04:10:00Z", 101, 102, 100, 100, 10));
        FeatureSnapshot snapshot = ENGINE.snapshot(
                FeatureTestSupport.context(1, bars, List.of(), List.of(), FeatureTestSupport.policy(), "M5"));
        assertThat(snapshot.feature(FeatureKeys.DIRECTIONAL_VOLUME_LONG).value()).isEqualTo(4.5);
        assertThat(snapshot.feature(FeatureKeys.DIRECTIONAL_VOLUME_SHORT).value()).isCloseTo(2.0 / 9.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
