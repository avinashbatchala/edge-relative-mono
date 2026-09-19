package com.edgerelative.application.history;

import com.edgerelative.application.history.HistoryRepository.ClaimedChunk;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bounded background worker for historical backfills.
 *
 * <p>A single dispatcher claims PENDING chunks and submits them to a small fixed worker pool with a
 * bounded queue. When the queue is full the chunk is released back to PENDING and the dispatcher
 * backs off, providing backpressure instead of unbounded fan-out or memory growth.
 */
@Component
public class BackfillWorker {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillWorker.class);

    private final HistoricalBackfillService service;
    private final HistoryProperties properties;

    private volatile boolean running;
    private ExecutorService workers;
    private Thread dispatcher;

    public BackfillWorker(HistoricalBackfillService service, HistoryProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        properties.validate();
        service.recoverStaleWork();
        workers = new ThreadPoolExecutor(
                properties.getWorkers(),
                properties.getWorkers(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(properties.getQueueCapacity()),
                Thread.ofPlatform().name("backfill-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        running = true;
        dispatcher = Thread.ofVirtual().name("backfill-dispatcher").start(this::dispatch);
        LOG.info("Historical backfill worker started ({} workers, queue {})", properties.getWorkers(), properties.getQueueCapacity());
    }

    private void dispatch() {
        while (running) {
            try {
                Optional<ClaimedChunk> claimed = service.claimNextChunk();
                if (claimed.isEmpty()) {
                    pause(properties.getSweepInterval());
                    continue;
                }
                ClaimedChunk chunk = claimed.get();
                try {
                    workers.execute(() -> process(chunk));
                } catch (RejectedExecutionException queueFull) {
                    // Backpressure: leave the chunk PENDING and try again after a short pause.
                    service.releaseChunk(chunk);
                    pause(properties.getSweepInterval());
                }
            } catch (RuntimeException transientFailure) {
                // e.g. database briefly unavailable; stay alive and retry.
                LOG.debug("Backfill dispatch paused: {}", transientFailure.getMessage());
                pause(properties.getSweepInterval());
            }
        }
    }

    private void process(ClaimedChunk chunk) {
        try {
            service.processChunk(chunk);
        } catch (RuntimeException failure) {
            LOG.warn("Backfill chunk {} failed: {}", chunk.coverageId(), failure.getMessage());
        }
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(Math.max(50L, duration.toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        if (dispatcher != null) {
            dispatcher.interrupt();
        }
        if (workers != null) {
            workers.shutdown();
            try {
                if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
