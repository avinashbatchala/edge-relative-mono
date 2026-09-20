package com.edgerelative.application.feature.engine;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-cardinality observability for the feature engine. Instrument ids are never metric tags
 * (DD-05 observability rules); logs may carry instrument context instead.
 */
public final class FeatureMetrics {

    private final Counter snapshots;
    private final Counter warmupFailures;
    private final Counter missingDependencies;
    private final Counter alignmentFailures;
    private final Counter qualityDowngrades;
    private final Counter persistenceDropped;
    private final AtomicLong persistenceQueueDepth = new AtomicLong();
    private final Timer snapshotLatency;

    public FeatureMetrics(MeterRegistry registry) {
        this.snapshots = Counter.builder("feature.snapshots").register(registry);
        this.warmupFailures = Counter.builder("feature.warmup.failures").register(registry);
        this.missingDependencies = Counter.builder("feature.missing.dependencies").register(registry);
        this.alignmentFailures = Counter.builder("feature.benchmark.alignment.failures").register(registry);
        this.qualityDowngrades = Counter.builder("feature.quality.downgrades").register(registry);
        this.persistenceDropped = Counter.builder("feature.persistence.dropped").register(registry);
        this.snapshotLatency = Timer.builder("feature.snapshot.latency").register(registry);
        registry.gauge("feature.persistence.queue.depth", persistenceQueueDepth);
    }

    public void recordSnapshot(Duration duration) {
        snapshots.increment();
        snapshotLatency.record(duration);
    }

    public void recordWarmupFailure() {
        warmupFailures.increment();
    }

    public void recordMissingDependency() {
        missingDependencies.increment();
    }

    public void recordAlignmentFailure() {
        alignmentFailures.increment();
    }

    public void recordQualityDowngrade() {
        qualityDowngrades.increment();
    }

    public void recordPersistenceDropped() {
        persistenceDropped.increment();
    }

    public void updatePersistenceQueueDepth(long depth) {
        persistenceQueueDepth.set(depth);
    }

    public long persistenceQueueDepth() {
        return persistenceQueueDepth.get();
    }
}
