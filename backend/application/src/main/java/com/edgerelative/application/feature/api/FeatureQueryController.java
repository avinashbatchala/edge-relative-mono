package com.edgerelative.application.feature.api;

import com.edgerelative.application.feature.service.FeatureDashboardService;
import com.edgerelative.application.feature.service.FeatureSnapshotService;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral feature read API for UI and research (DD-05 §48). It depends only on the feature
 * application service; no broker identifier or Groww type is exposed.
 */
@RestController
@RequestMapping("/api/v1/features")
public class FeatureQueryController {

    private static final int MAX_SERIES_POINTS = 5_000;

    private final FeatureSnapshotService service;
    private final FeatureDashboardService dashboard;

    public FeatureQueryController(FeatureSnapshotService service, FeatureDashboardService dashboard) {
        this.service = service;
        this.dashboard = dashboard;
    }

    /** Consolidated latest feature rows for the active watchlist (observational dashboard). */
    @GetMapping("/dashboard")
    public List<FeatureDashboardRow> dashboard() {
        return dashboard.rows();
    }

    /** Trust diagnostics for the displayed feature state. */
    @GetMapping("/diagnostics")
    public FeatureDiagnosticsResponse diagnostics() {
        return dashboard.diagnostics();
    }

    @GetMapping("/snapshot")
    public FeatureSnapshotResponse snapshot(
            @RequestParam long instrumentId,
            @RequestParam(defaultValue = FeatureSnapshotService.DEFAULT_TIMEFRAME) String timeframe,
            @RequestParam(required = false) Instant anchor) {
        return FeatureSnapshotResponse.from(service.snapshot(instrumentId, timeframe, anchor));
    }

    @GetMapping("/series")
    public List<FeatureSnapshotResponse> series(
            @RequestParam long instrumentId,
            @RequestParam(defaultValue = FeatureSnapshotService.DEFAULT_TIMEFRAME) String timeframe,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "2000") int limit) {
        List<FeatureSnapshotResponse> all = service.series(instrumentId, timeframe, from, to).stream()
                .map(FeatureSnapshotResponse::from)
                .toList();
        int capped = Math.clamp(limit, 1, MAX_SERIES_POINTS);
        if (all.size() <= capped) {
            return all;
        }
        return List.copyOf(all.subList(all.size() - capped, all.size()));
    }

    @GetMapping("/watchlist")
    public List<FeatureSnapshotResponse> watchlist() {
        return service.watchlistSnapshots().stream()
                .map(FeatureSnapshotResponse::from)
                .toList();
    }
}
