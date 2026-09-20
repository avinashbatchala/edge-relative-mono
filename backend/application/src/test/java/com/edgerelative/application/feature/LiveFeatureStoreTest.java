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
