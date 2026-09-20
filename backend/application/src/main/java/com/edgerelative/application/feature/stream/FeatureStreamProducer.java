package com.edgerelative.application.feature.stream;

import com.edgerelative.application.feature.api.FeatureDashboardRow;
import com.edgerelative.application.feature.service.FeatureDashboardService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Incremental feature-stream producer (DD-05 §151 live path). When enabled, it periodically recomputes
 * the authoritative dashboard rows and broadcasts a {@code feature.update} only when the content
 * actually changes, which advances the stream sequence and exercises the client gap/dedup logic.
 *
 * <p>Disabled by default: there is no live market-data producer yet, so the canonical store changes
 * only after ingestion/backfill. Enabling it before live ingestion would simply re-broadcast on
 * backfill. It reads through the same engine as the snapshot path — never a second formula.
 */
@Component
@ConditionalOnProperty(prefix = "feature.stream.producer", name = "enabled", havingValue = "true")
public class FeatureStreamProducer {

    private final FeatureDashboardService dashboard;
    private final FeatureStreamPublisher publisher;
    private final AtomicReference<Integer> lastSignature = new AtomicReference<>();

    public FeatureStreamProducer(FeatureDashboardService dashboard, FeatureStreamPublisher publisher) {
        this.dashboard = dashboard;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${feature.stream.producer.interval-ms:5000}")
    public void poll() {
        List<FeatureDashboardRow> rows = dashboard.rows(true);
        int signature = rows.hashCode();
        Integer previous = lastSignature.getAndSet(signature);
        if (previous == null || previous != signature) {
            publisher.broadcastUpdate(rows);
        }
    }
}
