package com.edgerelative.application.history;

import com.edgerelative.application.history.api.BackfillRunResponse;
import com.edgerelative.application.history.api.CoverageResponse;
import com.edgerelative.application.history.api.HistoryCandleResponse;
import com.edgerelative.application.history.api.StartBackfillRequest;
import com.edgerelative.broker.api.model.BrokerCandleInterval;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Broker-neutral historical data and research-dataset API. */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoricalBackfillService service;

    public HistoryController(HistoricalBackfillService service) {
        this.service = service;
    }

    @GetMapping("/coverage")
    public CoverageResponse coverage(
            @RequestParam long instrumentId, @RequestParam BrokerCandleInterval timeframe) {
        return service.coverage(instrumentId, timeframe);
    }

    @PostMapping("/backfill")
    public ResponseEntity<BackfillRunResponse> start(@RequestBody StartBackfillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(request));
    }

    @GetMapping("/backfill")
    public List<BackfillRunResponse> runs(
            @RequestParam long instrumentId, @RequestParam(defaultValue = "20") int limit) {
        return service.runs(instrumentId, limit);
    }

    @GetMapping("/backfill/{runKey}")
    public BackfillRunResponse run(@PathVariable String runKey) {
        return service.run(runKey);
    }

    @PostMapping("/backfill/{runKey}/retry")
    public BackfillRunResponse retry(@PathVariable String runKey) {
        return service.retry(runKey);
    }

    /** Persisted canonical candles for charts/backtests; never calls the broker. */
    @GetMapping("/candles")
    public List<HistoryCandleResponse> candles(
            @RequestParam long instrumentId,
            @RequestParam BrokerCandleInterval timeframe,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "5000") int limit) {
        return service.candles(instrumentId, timeframe, from, to, limit);
    }
}
