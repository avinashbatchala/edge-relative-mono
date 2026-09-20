package com.edgerelative.application.feature.persistence;

import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureMetrics;
import com.edgerelative.application.feature.policy.FeatureProperties;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bounded, ordered async persistence for rebuildable feature snapshots (DD-05 §38).
 *
 * <p>One dedicated thread preserves snapshot order. The queue is bounded and a full queue drops
 * persistence (never the calculation) and increments an observable counter, so persistence lag
 * degrades observably instead of exhausting memory. Feature persistence is not part of any financial
 * intent transaction. This is ordinary blocking I/O, which is exactly the established use of threads
 * here; feature calculation itself is never parallelised onto virtual threads.
 */
@Component
public class FeatureSnapshotWriter {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureSnapshotWriter.class);
    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(10);

    private final FeatureSnapshotStore store;
    private final FeatureMetrics metrics;
    private final boolean enabled;
    private final ThreadPoolExecutor executor;

    public FeatureSnapshotWriter(
            FeatureSnapshotStore store, FeatureMetrics metrics, FeatureProperties properties) {
        this.store = store;
        this.metrics = metrics;
        this.enabled = properties.getPersistence().isEnabled();
        int capacity = Math.max(1, properties.getPersistence().getQueueCapacity());
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "feature-persistence");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Non-blocking submit. Dropping is allowed because snapshots are rebuildable, and is observable.
     */
    public void write(FeatureSnapshot snapshot, long timeframeId, String sourceRevision) {
        if (!enabled) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    store.save(snapshot, timeframeId, sourceRevision);
                } catch (RuntimeException exception) {
                    LOG.warn("Feature snapshot persistence failed for instrument {}", snapshot.instrumentId(), exception);
                } finally {
                    metrics.updatePersistenceQueueDepth(executor.getQueue().size());
                }
            });
            metrics.updatePersistenceQueueDepth(executor.getQueue().size());
        } catch (RejectedExecutionException exception) {
            metrics.recordPersistenceDropped();
        }
    }

    /**
     * Blocks until the queue drains, bounded by a timeout; used by tests and shutdown.
     */
    public boolean flush() {
        long deadline = System.nanoTime() + FLUSH_TIMEOUT.toNanos();
        while ((!executor.getQueue().isEmpty() || executor.getActiveCount() > 0)
                && System.nanoTime() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return executor.getQueue().isEmpty() && executor.getActiveCount() == 0;
    }

    @PreDestroy
    public void shutdown() {
        flush();
        executor.shutdown();
    }
}
