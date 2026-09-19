package com.edgerelative.broker.groww.observability;

import com.edgerelative.broker.groww.resilience.GrowwOperation;
import com.edgerelative.broker.groww.resilience.GrowwRateLimitCategory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer instrumentation for the Groww adapter.
 *
 * <p>Tags are restricted to bounded values: broker, operation, category, outcome. Symbols, order ids,
 * request ids and exception messages are never used as tags.
 */
public class GrowwMetrics {

    private static final String BROKER = "groww";

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Timer rateLimitWait;

    public GrowwMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.rateLimitWait = Timer.builder("edge_relative.broker.rate_limit.wait")
                .description("Time spent waiting for Groww rate-limit admission")
                .tag("broker", BROKER)
                .publishPercentileHistogram()
                .register(registry);
        registry.gauge("edge_relative.broker.in_flight", inFlight);
        registry.gauge("edge_relative.broker.circuit.open", this, m -> 0);
    }

    public void recordRequest(
            GrowwOperation operation, GrowwRateLimitCategory category, Duration duration, String outcome) {
        Timer.builder("edge_relative.broker.request")
                .tag("broker", BROKER)
                .tag("operation", operation.name())
                .tag("category", category.name())
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
        counter("edge_relative.broker.requests", operation, category, outcome).increment();
        if ("success".equals(outcome)) {
            counter("edge_relative.broker.success", operation, category, outcome).increment();
        } else {
            counter("edge_relative.broker.failure", operation, category, outcome).increment();
        }
    }

    public void recordRetry(GrowwOperation operation) {
        registry.counter("edge_relative.broker.retries", "broker", BROKER, "operation", operation.name()).increment();
    }

    public void recordHttp429(GrowwOperation operation) {
        registry.counter("edge_relative.broker.http_429", "broker", BROKER, "operation", operation.name()).increment();
    }

    public void recordRateLimitRejection(GrowwRateLimitCategory category) {
        registry.counter("edge_relative.broker.rate_limit.rejected", "broker", BROKER, "category", category.name())
                .increment();
    }

    public void recordRateLimitWait(GrowwRateLimitCategory category, Duration duration) {
        rateLimitWait.record(duration);
        registry.counter("edge_relative.broker.rate_limit.waits", "broker", BROKER, "category", category.name())
                .increment();
    }

    public void recordCooldownActivation(GrowwRateLimitCategory category, Duration duration) {
        registry.counter("edge_relative.broker.cooldown.activated", "broker", BROKER, "category", category.name())
                .increment();
        registry.counter(
                        "edge_relative.broker.cooldown.duration",
                        "broker",
                        BROKER,
                        "category",
                        category.name())
                .increment(duration.toMillis());
    }

    public void recordAuthRefresh(String outcome) {
        registry.counter("edge_relative.broker.auth.refresh", "broker", BROKER, "outcome", outcome).increment();
    }

    public void recordCircuitTransition(String state) {
        registry.counter("edge_relative.broker.circuit.transition", "broker", BROKER, "state", state).increment();
    }

    public void incrementInFlight() {
        inFlight.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlight.decrementAndGet();
    }

    public int inFlight() {
        return inFlight.get();
    }

    private Counter counter(
            String name, GrowwOperation operation, GrowwRateLimitCategory category, String outcome) {
        String key = name + '|' + operation.name() + '|' + category.name() + '|' + outcome;
        return counters.computeIfAbsent(
                key,
                k -> registry.counter(
                        name,
                        "broker",
                        BROKER,
                        "operation",
                        operation.name(),
                        "category",
                        category.name(),
                        "outcome",
                        outcome));
    }
}
