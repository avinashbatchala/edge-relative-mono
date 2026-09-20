package com.edgerelative.application.feature.service;

import com.edgerelative.application.corporateaction.application.CorporateActionAdjustmentService;
import com.edgerelative.application.corporateaction.math.CorporateActionAdjustment.AdjustedCandle;
import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.engine.FeatureMetrics;
import com.edgerelative.application.feature.persistence.FeatureSnapshotWriter;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureProperties;
import com.edgerelative.application.feature.policy.FeatureVersions;
import com.edgerelative.application.history.AggregatedCandle;
import com.edgerelative.application.history.query.HistoricalDataReader;
import com.edgerelative.application.reference.CanonicalInstrumentService;
import com.edgerelative.application.reference.NseTradingCalendar;
import com.edgerelative.application.watchlist.api.WatchlistEntry;
import com.edgerelative.application.watchlist.WatchlistService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

/**
 * Broker-neutral orchestration for feature snapshots (DD-05 §51).
 *
 * <p>Reads canonical history through {@link HistoricalDataReader}, resolves benchmarks
 * point-in-time, and calls the one {@link FeatureEngine}. Snapshot and series both go through the
 * same engine, so there is no third "API shortcut formula". Persistence is submitted to a bounded
 * async writer and never blocks the caller's result.
 */
@Service
public class FeatureSnapshotService {

    private static final int HISTORY_LIMIT = 10_000;
    private static final String SOURCE_REVISION = "canonical-m1-v1";
    private static final String ADJUSTED_SOURCE_REVISION = "canonical-m1-ca-adjusted-v1";
    public static final String DEFAULT_TIMEFRAME = "M5";

    private final HistoricalDataReader reader;
    private final CanonicalInstrumentService canonical;
    private final FeatureReferenceResolver referenceResolver;
    private final FeaturePolicy policy;
    private final FeatureProperties properties;
    private final FeatureVersions versions;
    private final NseTradingCalendar calendar;
    private final FeatureEngine engine;
    private final FeatureSnapshotWriter writer;
    private final FeatureMetrics metrics;
    private final WatchlistService watchlist;
    private final CorporateActionAdjustmentService adjustments;
    private final Clock clock;

    public FeatureSnapshotService(
            HistoricalDataReader reader,
            CanonicalInstrumentService canonical,
            FeatureReferenceResolver referenceResolver,
            FeaturePolicy policy,
            FeatureProperties properties,
            FeatureVersions versions,
            NseTradingCalendar calendar,
            FeatureEngine engine,
            FeatureSnapshotWriter writer,
            FeatureMetrics metrics,
            WatchlistService watchlist,
            CorporateActionAdjustmentService adjustments,
            Clock clock) {
        this.reader = reader;
        this.canonical = canonical;
        this.referenceResolver = referenceResolver;
        this.policy = policy;
        this.properties = properties;
        this.versions = versions;
        this.calendar = calendar;
        this.engine = engine;
        this.writer = writer;
        this.metrics = metrics;
        this.watchlist = watchlist;
        this.adjustments = adjustments;
        this.clock = clock;
    }

    /**
     * Snapshot at (or before) {@code anchor}; {@code null} anchor means the latest canonical close.
     */
    public FeatureSnapshot snapshot(long instrumentId, String timeframe, Instant anchor) {
        return snapshot(instrumentId, timeframe, anchor, true);
    }

    /**
     * Snapshots computed for an observational dashboard are not persisted: a page load must not
     * append forty derived rows. Persistence remains for live/backfill computation.
     */
    public FeatureSnapshot snapshot(long instrumentId, String timeframe, Instant anchor, boolean persist) {
        return snapshot(instrumentId, timeframe, anchor, persist, new ConcurrentHashMap<>());
    }

    /**
     * Same as {@link #snapshot(long, String, Instant, boolean)} but shares a per-request candle
     * cache. A dashboard computes many instruments against the same benchmark; the cache collapses
     * those repeated reads into one without changing any calculation.
     */
    public FeatureSnapshot snapshot(
            long instrumentId,
            String timeframe,
            Instant anchor,
            boolean persist,
            ConcurrentMap<String, List<AggregatedCandle>> cache) {
        Instant effectiveAnchor = anchor == null ? clock.instant() : anchor;
        FeatureContext context = context(instrumentId, timeframe, effectiveAnchor, cache);
        long started = System.nanoTime();
        FeatureSnapshot snapshot = engine.snapshot(context);
        metrics.recordSnapshot(Duration.ofNanos(System.nanoTime() - started));
        if (persist) {
            writer.write(snapshot, canonical.ensureTimeframe(timeframe), sourceRevision());
        }
        return snapshot;
    }

    private String sourceRevision() {
        return properties.getCorporateActions().isAdjustedInputs() ? ADJUSTED_SOURCE_REVISION : SOURCE_REVISION;
    }

    /**
     * Deterministic reconstruction over a range using the same engine (DD-05 §51).
     */
    public List<FeatureSnapshot> series(long instrumentId, String timeframe, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new FeatureException(FeatureException.INVALID, "'from' must be before 'to'");
        }
        // Resolve the benchmark as of the range START for a historical series: using the range end
        // would let a sector/benchmark mapping that became valid only later leak into earlier bars
        // (DD-05 §141 point-in-time membership, §260 no future knowledge).
        FeatureContext context = context(
                instrumentId,
                timeframe,
                from,
                to,
                from.minus(Duration.ofDays(policy.historyDays())),
                new ConcurrentHashMap<>());
        List<FeatureSnapshot> all = engine.snapshots(context);
        List<FeatureSnapshot> result = new ArrayList<>();
        for (FeatureSnapshot snapshot : all) {
            Instant anchor = snapshot.anchorTimestamp();
            if (!anchor.isBefore(from) && !anchor.isAfter(to)) {
                result.add(snapshot);
            }
        }
        if (!result.isEmpty()) {
            FeatureSnapshot last = result.get(result.size() - 1);
            writer.write(last, canonical.ensureTimeframe(timeframe), sourceRevision());
        }
        return result;
    }

    /**
     * Latest snapshot for every active watchlist instrument.
     */
    public List<FeatureSnapshot> watchlistSnapshots() {
        Instant now = clock.instant();
        List<FeatureSnapshot> snapshots = new ArrayList<>();
        for (WatchlistEntry entry : watchlist.list().entries()) {
            snapshots.add(snapshot(entry.instrumentId(), DEFAULT_TIMEFRAME, now));
        }
        return snapshots;
    }

    private FeatureContext context(
            long instrumentId,
            String timeframe,
            Instant to,
            ConcurrentMap<String, List<AggregatedCandle>> cache) {
        return context(
                instrumentId,
                timeframe,
                to,
                to,
                to.minus(Duration.ofDays(policy.historyDays())),
                cache);
    }

    private FeatureContext context(
            long instrumentId,
            String timeframe,
            Instant to,
            Instant from,
            ConcurrentMap<String, List<AggregatedCandle>> cache) {
        return context(instrumentId, timeframe, to, to, from, cache);
    }

    /**
     * @param benchmarkAnchor the instant at which benchmark/sector identity is resolved. A single
     *                        snapshot uses its anchor; a historical series uses the range start so a
     *                        later mapping cannot leak backwards.
     */
    private FeatureContext context(
            long instrumentId,
            String timeframe,
            Instant benchmarkAnchor,
            Instant to,
            Instant from,
            ConcurrentMap<String, List<AggregatedCandle>> cache) {
        BenchmarkIdentity benchmark =
                referenceResolver.resolve(instrumentId, benchmarkAnchor, policy.benchmark().marketCode());
        List<AggregatedCandle> subject =
                load(cache, instrumentId, timeframe, from, to, HISTORY_LIMIT);
        List<AggregatedCandle> market = benchmark.hasMarket()
                ? load(cache, benchmark.marketInstrumentId(), timeframe, from, to, HISTORY_LIMIT)
                : List.of();
        List<AggregatedCandle> sector = benchmark.hasSector()
                ? load(cache, benchmark.sectorInstrumentId(), timeframe, from, to, HISTORY_LIMIT)
                : List.of();
        return new FeatureContext(
                instrumentId,
                timeframe,
                subject,
                market,
                sector,
                benchmark,
                policy,
                versions,
                calendar,
                benchmark.marketCode(),
                benchmark.sectorCode());
    }

    /**
     * Subject candles for the same window a snapshot uses, served from the shared per-request cache.
     * Callers can derive display values (e.g. last close) without re-reading the series.
     */
    public List<AggregatedCandle> subjectCandles(
            long instrumentId,
            String timeframe,
            Instant to,
            ConcurrentMap<String, List<AggregatedCandle>> cache) {
        Instant from = to.minus(Duration.ofDays(policy.historyDays()));
        return load(cache, instrumentId, timeframe, from, to, HISTORY_LIMIT);
    }

    /**
     * De-duplicates identical canonical reads within one request. A dashboard computes every
     * instrument against the same benchmark for the same window, so without this the benchmark is
     * re-read once per instrument.
     */
    private List<AggregatedCandle> load(
            ConcurrentMap<String, List<AggregatedCandle>> cache,
            long instrumentId,
            String timeframe,
            Instant from,
            Instant to,
            int limit) {
        boolean adjusted = properties.getCorporateActions().isAdjustedInputs();
        String key = instrumentId + "|" + timeframe + "|" + from + "|" + to + "|" + limit + "|ca=" + adjusted;
        return cache.computeIfAbsent(key, ignored -> adjusted
                ? adjustments.adjustedCandles(instrumentId, timeframe, from, to, limit, to).stream()
                        .map(AdjustedCandle::candle)
                        .toList()
                : reader.candles(instrumentId, timeframe, from, to, limit));
    }
}
