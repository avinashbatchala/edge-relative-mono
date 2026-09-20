package com.edgerelative.application.feature.api;

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

    private final FeatureSnapshotService service;

    public FeatureQueryController(FeatureSnapshotService service) {
        this.service = service;
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
            @RequestParam Instant to) {
        return service.series(instrumentId, timeframe, from, to).stream()
                .map(FeatureSnapshotResponse::from)
                .toList();
    }

    @GetMapping("/watchlist")
    public List<FeatureSnapshotResponse> watchlist() {
        return service.watchlistSnapshots().stream()
                .map(FeatureSnapshotResponse::from)
                .toList();
    }
}
