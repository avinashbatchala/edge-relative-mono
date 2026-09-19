package com.edgerelative.application.history;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backfill resource limits. A backfill is a low-priority background workload: correctness and
 * not overwhelming Groww/DB/CPU take priority over speed.
 */
@ConfigurationProperties(prefix = "history.backfill")
public class HistoryProperties {

    /** Small, fixed worker count. */
    private int workers = 2;
    /** Bounded queue; submission blocks/rejects to apply backpressure. */
    private int queueCapacity = 50;
    /** How often the dispatcher retries when no work is available. */
    private Duration sweepInterval = Duration.ofSeconds(2);
    /** Max candles written per database batch. */
    private int insertBatchSize = 1000;
    /** Max M1 rows loaded to build a derived series; bounds a single read-path request. */
    private int maxSourceCandles = 500_000;

    public int getWorkers() {
        return workers;
    }

    public void setWorkers(int workers) {
        this.workers = workers;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Duration getSweepInterval() {
        return sweepInterval;
    }

    public void setSweepInterval(Duration sweepInterval) {
        this.sweepInterval = sweepInterval;
    }

    public int getInsertBatchSize() {
        return insertBatchSize;
    }

    public void setInsertBatchSize(int insertBatchSize) {
        this.insertBatchSize = insertBatchSize;
    }

    public int getMaxSourceCandles() {
        return maxSourceCandles;
    }

    public void setMaxSourceCandles(int maxSourceCandles) {
        this.maxSourceCandles = maxSourceCandles;
    }

    public void validate() {
        if (workers < 1) {
            throw new IllegalStateException("history.backfill.workers must be >= 1");
        }
        if (queueCapacity < 1) {
            throw new IllegalStateException("history.backfill.queue-capacity must be >= 1");
        }
        if (insertBatchSize < 1) {
            throw new IllegalStateException("history.backfill.insert-batch-size must be >= 1");
        }
        if (maxSourceCandles < 1) {
            throw new IllegalStateException("history.backfill.max-source-candles must be >= 1");
        }
        if (sweepInterval == null || sweepInterval.isNegative() || sweepInterval.isZero()) {
            throw new IllegalStateException("history.backfill.sweep-interval must be positive");
        }
    }
}
