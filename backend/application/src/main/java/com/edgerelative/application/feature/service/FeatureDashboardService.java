package com.edgerelative.application.feature.service;

import com.edgerelative.application.feature.api.FeatureDashboardRow;
import com.edgerelative.application.feature.api.FeatureDiagnosticsResponse;
import com.edgerelative.application.feature.domain.ContextSnapshot;
import com.edgerelative.application.feature.domain.FeatureAvailability;
import com.edgerelative.application.feature.domain.FeatureKeys;
import com.edgerelative.application.feature.domain.FeatureQuality;
import com.edgerelative.application.feature.domain.FeatureSchemaVersions;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.domain.FeatureValue;
import com.edgerelative.application.feature.engine.FeatureMetrics;
import com.edgerelative.application.feature.persistence.FeatureSnapshotWriter;
import com.edgerelative.application.feature.policy.CalculationVersions;
import com.edgerelative.application.feature.policy.FeatureDashboardExecutor;
import com.edgerelative.application.feature.policy.FeatureProperties;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.watchlist.WatchlistService;
import com.edgerelative.application.watchlist.api.WatchlistEntry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

/**
 * Read-only aggregation for the operator Feature Dashboard (DD-05 observability).
 *
 * <p>Computes the latest M5 and D1 snapshots per active watchlist instrument through the one
 * {@link FeatureSnapshotService} engine and maps them to a dashboard row. It is observational: no
 * strategy, risk or execution decision is made here, and dashboard computation is not persisted.
 * Missing metrics stay {@code null} with an explicit reason.
 */
@Service
public class FeatureDashboardService {

    public static final String DEFAULT_TIMEFRAME = FeatureSnapshotService.DEFAULT_TIMEFRAME;
    private static final String DAILY = "D1";
    /** Caps concurrent per-instrument DB work; bounded far below the watchlist capacity. */
    private static final int DASHBOARD_CONCURRENCY = 8;

    private final WatchlistService watchlist;
    private final FeatureSnapshotService snapshots;
    private final FeatureMetrics metrics;
    private final FeatureSnapshotWriter writer;
    private final Clock clock;
    private final FeatureDashboardExecutor featureDashboardExecutor;
    private final Duration cacheTtl;
    private final ReentrantLock cacheLock = new ReentrantLock();
    private volatile List<FeatureDashboardRow> cachedRows;
    private volatile Instant cachedAt;

    public FeatureDashboardService(
            WatchlistService watchlist,
            FeatureSnapshotService snapshots,
            FeatureMetrics metrics,
            FeatureSnapshotWriter writer,
            Clock clock,
            FeatureDashboardExecutor featureDashboardExecutor,
            FeatureProperties properties) {
        this.watchlist = watchlist;
        this.snapshots = snapshots;
        this.metrics = metrics;
        this.writer = writer;
        this.clock = clock;
        this.featureDashboardExecutor = featureDashboardExecutor;
        this.cacheTtl = properties.getDashboard().getCacheTtl();
    }

    public List<FeatureDashboardRow> rows() {
        return rows(false);
    }

    /**
     * Single-flight, short-TTL cache: dashboard, diagnostics and the stream snapshot share one
     * on-demand computation instead of each recomputing the whole watchlist. {@code refresh} forces a
     * fresh computation (and repopulates the cache).
     */
    public List<FeatureDashboardRow> rows(boolean refresh) {
        if (!refresh) {
            List<FeatureDashboardRow> cached = freshCache();
            if (cached != null) {
                return cached;
            }
        }
        cacheLock.lock();
        try {
            if (!refresh) {
                List<FeatureDashboardRow> cached = freshCache();
                if (cached != null) {
                    return cached;
                }
            }
            List<FeatureDashboardRow> computed = computeRows(clock.instant());
            cachedRows = computed;
            cachedAt = clock.instant();
            return computed;
        } finally {
            cacheLock.unlock();
        }
    }

    private List<FeatureDashboardRow> freshCache() {
        List<FeatureDashboardRow> cached = cachedRows;
        Instant at = cachedAt;
        if (cached == null || at == null) {
            return null;
        }
        return Duration.between(at, clock.instant()).compareTo(cacheTtl) < 0 ? cached : null;
    }

    /**
     * Each instrument is an independent set of blocking reads, so they run concurrently on virtual
     * threads (bounded) while sharing one per-request candle cache. Previously this was a serial
     * fan-out and dominated dashboard latency.
     */
    private List<FeatureDashboardRow> computeRows(Instant now) {
        List<WatchlistEntry> entries = watchlist.list().entries();
        if (entries.isEmpty()) {
            return List.of();
        }
        ConcurrentMap<String, List<AggregatedCandle>> seriesCache = new ConcurrentHashMap<>();
        Semaphore permits = new Semaphore(Math.min(entries.size(), DASHBOARD_CONCURRENCY), true);
        List<CompletableFuture<FeatureDashboardRow>> futures = new ArrayList<>(entries.size());
        for (WatchlistEntry entry : entries) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            permits.acquire();
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return unavailableRow(entry, now, "interrupted");
                        }
                        try {
                            return row(entry, now, seriesCache);
                        } finally {
                            permits.release();
                        }
                    },
                    featureDashboardExecutor.executor()));
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    public FeatureDiagnosticsResponse diagnostics() {
        return diagnostics(false);
    }

    public FeatureDiagnosticsResponse diagnostics(boolean refresh) {
        Instant now = clock.instant();
        List<FeatureDashboardRow> rows = rows(refresh);
        Map<String, Integer> stateCounts = new LinkedHashMap<>();
        List<FeatureDiagnosticsResponse.InstrumentState> states = new ArrayList<>();
        long latestSeconds = Long.MAX_VALUE;
        long oldestSeconds = 0L;
        int healthy = 0;
        for (FeatureDashboardRow row : rows) {
            String state = classify(row);
            stateCounts.merge(state, 1, Integer::sum);
            if ("HEALTHY".equals(state)) {
                healthy++;
            }
            Long stale = row.staleSeconds();
            if (stale != null) {
                latestSeconds = Math.min(latestSeconds, stale);
                oldestSeconds = Math.max(oldestSeconds, stale);
            }
            states.add(new FeatureDiagnosticsResponse.InstrumentState(
                    row.instrumentId(), row.symbol(), state, reasonOf(row), stale, row.quality()));
        }
        FeatureDiagnosticsResponse.Counters counters = new FeatureDiagnosticsResponse.Counters(
                (long) metrics.snapshotCount(),
                (long) metrics.warmupFailureCount(),
                (long) metrics.missingDependencyCount(),
                (long) metrics.alignmentFailureCount(),
                (long) metrics.qualityDowngradeCount());
        return new FeatureDiagnosticsResponse(
                now,
                "UP",
                DEFAULT_TIMEFRAME,
                rows.size(),
                healthy,
                stateCounts,
                metricGaps(rows),
                rows.isEmpty() ? null : latestSeconds,
                rows.isEmpty() ? null : oldestSeconds,
                writer.queueDepth(),
                (long) metrics.persistenceDroppedCount(),
                counters,
                new FeatureDiagnosticsResponse.Versions(FeatureSchemaVersions.CURRENT, CalculationVersions.CURRENT),
                states,
                List.of(
                        "Features are computed on demand from canonical candles (dashboard, diagnostics, "
                                + "series, and the authoritative stream snapshot on connect/resync). There is "
                                + "no live market-data event producer yet, so no incremental recalculation "
                                + "occurs; sequence stays 0 until a producer broadcasts feature.update.",
                        "Stale/aggregate quality reflects the newest canonical candle, not a live feed. It "
                                + "clears only when fresh canonical data is ingested for the watchlist.",
                        "The engine counters (snapshots/warm-up/missing/alignment/quality) are cumulative "
                                + "since process start and are not incremented by dashboard reads; use the "
                                + "current state counts and per-metric gaps for the displayed state.",
                        "VWAP distance in ATR units has no producer: VWAP is not implemented in the feature engine."));
    }

    /**
     * One instrument's failure (for example no canonical candles yet) must not blank the whole
     * dashboard: it becomes an explicit UNAVAILABLE row with a reason.
     */
    private FeatureDashboardRow row(
            WatchlistEntry entry,
            Instant now,
            ConcurrentMap<String, List<AggregatedCandle>> seriesCache) {
        try {
            return computedRow(entry, now, seriesCache);
        } catch (RuntimeException failure) {
            return unavailableRow(entry, now, failure.getMessage());
        }
    }

    private FeatureDashboardRow computedRow(
            WatchlistEntry entry,
            Instant now,
            ConcurrentMap<String, List<AggregatedCandle>> seriesCache) {
        FeatureSnapshot snapshot =
                snapshots.snapshot(entry.instrumentId(), DEFAULT_TIMEFRAME, now, false, seriesCache);
        FeatureSnapshot daily = snapshots.snapshot(entry.instrumentId(), DAILY, now, false, seriesCache);

        Map<String, String> unavailable = new LinkedHashMap<>();
        Double atr = metric(snapshot, FeatureKeys.ATR, unavailable);
        // Reuse the series already loaded for the snapshots rather than re-reading the price.
        Double lastPrice = lastClose(
                snapshots.subjectCandles(entry.instrumentId(), DEFAULT_TIMEFRAME, now, seriesCache));
        Double previousClose = previousClose(
                snapshots.subjectCandles(entry.instrumentId(), DAILY, now, seriesCache));
        Double priceChange = lastPrice != null && previousClose != null ? lastPrice - previousClose : null;
        Double priceChangePercent =
                priceChange != null && previousClose != null && previousClose != 0.0
                        ? priceChange / previousClose * 100.0
                        : null;
        Double atrPercent = atr != null && lastPrice != null && lastPrice != 0.0 ? atr / lastPrice * 100.0 : null;
        Double vwapDistanceAtr = null;
        unavailable.put("VWAP_DISTANCE_ATR", "VWAP feature is not implemented");

        Map<String, String> versions = new LinkedHashMap<>();
        snapshot.features().forEach((key, value) -> versions.put(key, value.version().displayVersion()));

        return new FeatureDashboardRow(
                entry.instrumentId(),
                entry.instrumentKey() == null ? null : entry.instrumentKey().toString(),
                entry.symbol(),
                entry.name(),
                entry.exchange(),
                entry.segment(),
                entry.instrumentType(),
                DEFAULT_TIMEFRAME,
                snapshot.anchorTimestamp(),
                now,
                lastPrice,
                previousClose,
                priceChange,
                priceChangePercent,
                metric(snapshot, FeatureKeys.RRS_RAW, unavailable),
                metric(snapshot, FeatureKeys.RRS_FAST, unavailable),
                metric(snapshot, FeatureKeys.RRS_SLOW, unavailable),
                metric(snapshot, FeatureKeys.RRS_PERSISTENCE, unavailable),
                label(snapshot, null, FeatureKeys.RRS_TREND_STATE, unavailable),
                dailyRrsState(daily, unavailable),
                metric(snapshot, FeatureKeys.RVOL_INTERVAL, unavailable),
                metric(snapshot, FeatureKeys.RVOL_CUMULATIVE, unavailable),
                metric(snapshot, FeatureKeys.RVE, unavailable),
                atr,
                atrPercent,
                vwapDistanceAtr,
                label(snapshot, snapshot.market(), FeatureKeys.MARKET_PRICE_STRUCTURE, unavailable),
                label(snapshot, snapshot.sector(), FeatureKeys.SECTOR_PRICE_STRUCTURE, unavailable),
                contextMetric(snapshot.sector(), FeatureKeys.SECTOR_RRS_RAW, unavailable),
                snapshot.quality().name(),
                snapshot.availability().name(),
                reasonOf(snapshot),
                snapshot.anchorTimestamp() == null
                        ? null
                        : Math.max(0L, Duration.between(snapshot.anchorTimestamp(), now).getSeconds()),
                snapshot.featureSchemaVersion(),
                versions,
                unavailable);
    }

    private static FeatureDashboardRow unavailableRow(WatchlistEntry entry, Instant now, String message) {
        String reason = message == null || message.isBlank() ? "no canonical observations" : message;
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String key : List.of(
                FeatureKeys.ATR,
                FeatureKeys.RRS_RAW,
                FeatureKeys.RRS_FAST,
                FeatureKeys.RRS_SLOW,
                FeatureKeys.RRS_PERSISTENCE,
                FeatureKeys.RVOL_INTERVAL,
                FeatureKeys.RVOL_CUMULATIVE,
                FeatureKeys.RVE,
                FeatureKeys.RRS_TREND_STATE,
                FeatureKeys.MARKET_PRICE_STRUCTURE,
                FeatureKeys.SECTOR_PRICE_STRUCTURE,
                FeatureKeys.SECTOR_RRS_RAW)) {
            reasons.put(key, reason);
        }
        reasons.put("DAILY_RRS_STATE", reason);
        reasons.put("VWAP_DISTANCE_ATR", "VWAP feature is not implemented");
        return new FeatureDashboardRow(
                entry.instrumentId(),
                entry.instrumentKey() == null ? null : entry.instrumentKey().toString(),
                entry.symbol(),
                entry.name(),
                entry.exchange(),
                entry.segment(),
                entry.instrumentType(),
                DEFAULT_TIMEFRAME,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FeatureQuality.UNAVAILABLE.name(),
                FeatureAvailability.MISSING_INPUT.name(),
                reason,
                null,
                FeatureSchemaVersions.CURRENT,
                Map.of(),
                reasons);
    }

    /**
     * Current per-metric gap counts across the displayed rows. Unlike the cumulative engine counters,
     * these describe the state the operator is looking at right now, so they do not inflate on each
     * dashboard render.
     */
    private static Map<String, Integer> metricGaps(List<FeatureDashboardRow> rows) {
        List<String> tracked = List.of(
                FeatureKeys.RRS_RAW,
                FeatureKeys.RVOL_INTERVAL,
                FeatureKeys.RVOL_CUMULATIVE,
                FeatureKeys.RVE,
                FeatureKeys.ATR,
                "DAILY_RRS_STATE",
                "VWAP_DISTANCE_ATR",
                FeatureKeys.MARKET_PRICE_STRUCTURE,
                FeatureKeys.SECTOR_RRS_RAW);
        Map<String, Integer> gaps = new LinkedHashMap<>();
        for (String key : tracked) {
            int count = 0;
            for (FeatureDashboardRow row : rows) {
                if (row.unavailableReasons().containsKey(key)) {
                    count++;
                }
            }
            gaps.put(key, count);
        }
        return gaps;
    }

    private static Double metric(FeatureSnapshot snapshot, String key, Map<String, String> unavailable) {
        return value(snapshot.features().get(key), key, unavailable);
    }

    private static Double contextMetric(ContextSnapshot context, String key, Map<String, String> unavailable) {
        if (context == null) {
            unavailable.putIfAbsent(key, "sector benchmark not resolved");
            return null;
        }
        return value(context.features().get(key), key, unavailable);
    }

    private static Double value(FeatureValue value, String key, Map<String, String> unavailable) {
        if (value == null) {
            unavailable.putIfAbsent(key, "not calculated");
            return null;
        }
        if (!value.availability().hasValue() || value.value() == null) {
            unavailable.putIfAbsent(key, reason(value));
            return null;
        }
        return value.value();
    }

    private static String label(
            FeatureSnapshot snapshot, ContextSnapshot context, String key, Map<String, String> unavailable) {
        FeatureValue value = key.equals(FeatureKeys.RRS_TREND_STATE)
                ? snapshot.features().get(key)
                : context == null ? null : context.features().get(key);
        if (value == null || !value.availability().hasValue() || value.label() == null) {
            String reason;
            if (value != null) {
                reason = reason(value);
            } else if (key.startsWith("MARKET")) {
                reason = "broad-market benchmark not resolved";
            } else if (key.startsWith("SECTOR")) {
                reason = "sector benchmark not resolved";
            } else {
                reason = "not calculated";
            }
            unavailable.putIfAbsent(key, reason);
            return null;
        }
        return value.label();
    }

    private static String dailyRrsState(FeatureSnapshot daily, Map<String, String> unavailable) {
        FeatureValue raw = daily.features().get(FeatureKeys.RRS_RAW);
        if (raw == null || !raw.availability().hasValue() || raw.value() == null) {
            unavailable.putIfAbsent("DAILY_RRS_STATE", raw == null ? "not calculated" : reason(raw));
            return null;
        }
        if (raw.value() > 0) {
            return "POSITIVE";
        }
        if (raw.value() < 0) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private static Double lastClose(List<AggregatedCandle> candles) {
        if (candles.isEmpty()) {
            return null;
        }
        AggregatedCandle last = candles.get(candles.size() - 1);
        return last.close() == null ? null : last.close().doubleValue();
    }

    private static Double previousClose(List<AggregatedCandle> daily) {
        if (daily.size() < 2) {
            return null;
        }
        AggregatedCandle previous = daily.get(daily.size() - 2);
        return previous.close() == null ? null : previous.close().doubleValue();
    }

    private static String reason(FeatureValue value) {
        String reason = value.lineage().get("reason");
        if (reason != null) {
            return reason;
        }
        return value.availability() == FeatureAvailability.VALID
                ? value.quality().name()
                : value.availability().name();
    }

    private static String reasonOf(FeatureSnapshot snapshot) {
        if (snapshot.availability() == FeatureAvailability.VALID && snapshot.quality().trustworthy()) {
            return null;
        }
        for (FeatureValue value : snapshot.features().values()) {
            if (!value.availability().hasValue()) {
                return reason(value);
            }
        }
        return snapshot.quality().name();
    }

    private static String reasonOf(FeatureDashboardRow row) {
        if (row.qualityReason() != null) {
            return row.qualityReason();
        }
        return row.availability();
    }

    static String classify(FeatureDashboardRow row) {
        FeatureAvailability availability = FeatureAvailability.valueOf(row.availability());
        FeatureQuality quality = FeatureQuality.valueOf(row.quality());
        if (quality == FeatureQuality.UNAVAILABLE || availability == FeatureAvailability.MISSING_INPUT) {
            return "UNAVAILABLE";
        }
        if (availability == FeatureAvailability.WARMING_UP
                || availability == FeatureAvailability.INSUFFICIENT_HISTORY) {
            return "WARMING_UP";
        }
        if (availability == FeatureAvailability.STALE || quality == FeatureQuality.STALE) {
            return "STALE";
        }
        if (availability == FeatureAvailability.INVALID) {
            return "INVALID";
        }
        if (availability != FeatureAvailability.VALID || !quality.trustworthy()) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }
}
