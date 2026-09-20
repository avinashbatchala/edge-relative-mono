package com.edgerelative.application.feature;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContextTemplate;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.engine.LiveFeatureStore;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.history.AggregatedCandle;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LiveFeatureStoreTest {

    private static final LocalDate D = LocalDate.of(2026, 9, 1);

    private static FeatureContextTemplate template(long instrumentId) {
        FeaturePolicy policy = FeatureTestSupport.policy();
        return new FeatureContextTemplate(
                instrumentId, "M5", null, "NIFTY50", null, null, null, null,
                policy, new FeatureVersions(policy), FeatureTestSupport.CALENDAR);
    }

    @Test
    void duplicateEventDoesNotDoubleAdvanceState() {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 1000);
        List<AggregatedCandle> bars = FeatureTestSupport.session(D, 100);
        FeatureContextTemplate template = template(1);
        store.accept(template, bars.get(0));
        FeatureSnapshot first = store.accept(template, bars.get(0));
        FeatureSnapshot second = store.accept(template, bars.get(0));
        assertThat(store.history(1L, "M5")).hasSize(1);
        assertThat(store.rejectedEvents(1L, "M5")).isEqualTo(2);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void outOfOrderEventIsIgnored() {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 1000);
        List<AggregatedCandle> bars = FeatureTestSupport.session(D, 100);
        FeatureContextTemplate template = template(1);
        store.accept(template, bars.get(5));
        store.accept(template, bars.get(2));
        assertThat(store.history(1L, "M5")).hasSize(1);
        assertThat(store.rejectedEvents(1L, "M5")).isEqualTo(1);
    }

    @Test
    void identicalReplayIsCountedAsDuplicateSeparatelyFromLateEvents() {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 1000);
        List<AggregatedCandle> bars = FeatureTestSupport.session(D, 100);
        FeatureContextTemplate template = template(1);
        store.accept(template, bars.get(5));
        store.accept(template, bars.get(5));
        store.accept(template, bars.get(2));
        assertThat(store.history(1L, "M5")).hasSize(1);
        assertThat(store.duplicateEvents(1L, "M5")).isEqualTo(1);
        assertThat(store.lateEvents(1L, "M5")).isEqualTo(1);
        assertThat(store.conflictingEvents(1L, "M5")).isZero();
        assertThat(store.rejectedEvents(1L, "M5")).isEqualTo(2);
    }

    @Test
    void sameTimestampWithDifferentValuesIsFlaggedConflictingAndNeverAveragedIn() {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 1000);
        List<AggregatedCandle> bars = FeatureTestSupport.session(D, 100);
        FeatureContextTemplate template = template(1);
        AggregatedCandle original = bars.get(5);
        store.accept(template, original);
        AggregatedCandle conflicting = FeatureTestSupport.bar(
                original.openTime().toString(),
                original.closeTime().toString(),
                original.open().doubleValue(),
                original.high().doubleValue(),
                original.low().doubleValue(),
                original.close().doubleValue() + 1.0,
                original.volume() + 50);
        store.accept(template, conflicting);
        assertThat(store.conflictingEvents(1L, "M5")).isEqualTo(1);
        assertThat(store.duplicateEvents(1L, "M5")).isZero();
        assertThat(store.history(1L, "M5")).containsExactly(original);
    }

    @Test
    void boundedBufferKeepsTheNewestBarsUnderPressure() {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 10);
        List<AggregatedCandle> bars = FeatureTestSupport.session(D, 100);
        FeatureContextTemplate template = template(1);
        for (AggregatedCandle bar : bars) {
            store.accept(template, bar);
        }
        assertThat(store.history(1L, "M5")).hasSize(10);
        assertThat(store.history(1L, "M5").get(9)).isEqualTo(bars.get(74));
        assertThat(store.rejectedEvents(1L, "M5")).isZero();
    }

    @Test
    void acceptedVolumeMatchesAnIndependentHandOrderedLedger() {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 1000);
        FeatureContextTemplate template = template(1);
        AggregatedCandle b1 = FeatureTestSupport.bar(
                "2026-09-01T03:45:00Z", "2026-09-01T03:50:00Z", 100, 101, 99, 100, 10);
        AggregatedCandle b2 = FeatureTestSupport.bar(
                "2026-09-01T03:50:00Z", "2026-09-01T03:55:00Z", 100, 101, 99, 100, 20);
        AggregatedCandle b3 = FeatureTestSupport.bar(
                "2026-09-01T03:55:00Z", "2026-09-01T04:00:00Z", 100, 101, 99, 100, 30);
        AggregatedCandle b4 = FeatureTestSupport.bar(
                "2026-09-01T04:00:00Z", "2026-09-01T04:05:00Z", 100, 101, 99, 100, 40);

        // Hand-ordered ledger: an out-of-order first event, a duplicate replay, a late replay and a
        // late duplicate must never double-count volume or move the accepted close time backwards.
        store.accept(template, b2);
        store.accept(template, b1);
        store.accept(template, b2);
        store.accept(template, b3);
        store.accept(template, b2);
        store.accept(template, b3);
        store.accept(template, b4);

        List<AggregatedCandle> accepted = store.history(1L, "M5");
        long expectedUniqueVolume = 20 + 30 + 40;
        assertThat(accepted).containsExactly(b2, b3, b4);
        assertThat(accepted.stream().mapToLong(AggregatedCandle::volume).sum()).isEqualTo(expectedUniqueVolume);
        assertThat(store.duplicateEvents(1L, "M5")).isEqualTo(2);
        assertThat(store.lateEvents(1L, "M5")).isEqualTo(2);
        assertThat(store.rejectedEvents(1L, "M5")).isEqualTo(4);
    }

    @Test
    void concurrentSymbolUpdatesDoNotCorruptEachOther() throws Exception {
        LiveFeatureStore store = new LiveFeatureStore(new FeatureEngine(), 1000);
        List<AggregatedCandle> barsA = FeatureTestSupport.session(D, 100);
        List<AggregatedCandle> barsB = FeatureTestSupport.session(D, 200);
        FeatureContextTemplate templateA = template(1);
        FeatureContextTemplate templateB = template(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var futureA = pool.submit(() -> feed(store, templateA, barsA, start));
            var futureB = pool.submit(() -> feed(store, templateB, barsB, start));
            start.countDown();
            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(store.history(1L, "M5")).hasSize(75);
        assertThat(store.history(2L, "M5")).hasSize(75);
        assertThat(store.rejectedEvents(1L, "M5")).isZero();
        assertThat(store.rejectedEvents(2L, "M5")).isZero();
    }

    private static void feed(
            LiveFeatureStore store,
            FeatureContextTemplate template,
            List<AggregatedCandle> bars,
            CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        for (AggregatedCandle bar : bars) {
            store.accept(template, bar);
        }
    }
}
