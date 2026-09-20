package com.edgerelative.application.feature.service;

import com.edgerelative.application.feature.domain.BenchmarkIdentity;
import com.edgerelative.application.feature.domain.FeatureSnapshot;
import com.edgerelative.application.feature.engine.FeatureContext;
import com.edgerelative.application.feature.engine.FeatureEngine;
import com.edgerelative.application.feature.engine.FeatureMetrics;
import com.edgerelative.application.feature.persistence.FeatureSnapshotWriter;
import com.edgerelative.application.feature.policy.FeaturePolicy;
import com.edgerelative.application.feature.policy.FeatureVersions;
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
    public static final String DEFAULT_TIMEFRAME = "M5";

    private final HistoricalDataReader reader;
    private final CanonicalInstrumentService canonical;
    private final FeatureReferenceResolver referenceResolver;
    private final FeaturePolicy policy;
    private final FeatureVersions versions;
    private final NseTradingCalendar calendar;
    private final FeatureEngine engine;
    private final FeatureSnapshotWriter writer;
    private final FeatureMetrics metrics;
    private final WatchlistService watchlist;
    private final Clock clock;

    public FeatureSnapshotService(
            HistoricalDataReader reader,
            CanonicalInstrumentService canonical,
            FeatureReferenceResolver referenceResolver,
            FeaturePolicy policy,
            FeatureVersions versions,
            NseTradingCalendar calendar,
            FeatureEngine engine,
            FeatureSnapshotWriter writer,
            FeatureMetrics metrics,
            WatchlistService watchlist,
            Clock clock) {
        this.reader = reader;
        this.canonical = canonical;
        this.referenceResolver = referenceResolver;
        this.policy = policy;
        this.versions = versions;
        this.calendar = calendar;
        this.engine = engine;
        this.writer = writer;
        this.metrics = metrics;
        this.watchlist = watchlist;
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
        Instant effectiveAnchor = anchor == null ? clock.instant() : anchor;
        FeatureContext context = context(instrumentId, timeframe, effectiveAnchor);
        long started = System.nanoTime();
        FeatureSnapshot snapshot = engine.snapshot(context);
        metrics.recordSnapshot(Duration.ofNanos(System.nanoTime() - started));
        if (persist) {
            writer.write(snapshot, canonical.ensureTimeframe(timeframe), SOURCE_REVISION);
        }
        return snapshot;
    }

    /**
     * Deterministic reconstruction over a range using the same engine (DD-05 §51).
     */
    public List<FeatureSnapshot> series(long instrumentId, String timeframe, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new FeatureException(FeatureException.INVALID, "'from' must be before 'to'");
        }
        FeatureContext context = context(instrumentId, timeframe, to, from.minus(Duration.ofDays(policy.historyDays())));
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
            writer.write(last, canonical.ensureTimeframe(timeframe), SOURCE_REVISION);
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

    private FeatureContext context(long instrumentId, String timeframe, Instant to) {
        return context(instrumentId, timeframe, to, to.minus(Duration.ofDays(policy.historyDays())));
    }

    private FeatureContext context(long instrumentId, String timeframe, Instant to, Instant from) {
        BenchmarkIdentity benchmark =
                referenceResolver.resolve(instrumentId, to, policy.benchmark().marketCode());
        List<com.edgerelative.application.history.AggregatedCandle> subject =
                reader.candles(instrumentId, timeframe, from, to, HISTORY_LIMIT);
        List<com.edgerelative.application.history.AggregatedCandle> market = benchmark.hasMarket()
                ? reader.candles(benchmark.marketInstrumentId(), timeframe, from, to, HISTORY_LIMIT)
                : List.of();
        List<com.edgerelative.application.history.AggregatedCandle> sector = benchmark.hasSector()
                ? reader.candles(benchmark.sectorInstrumentId(), timeframe, from, to, HISTORY_LIMIT)
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
}
