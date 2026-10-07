package com.edgerelative.application.backtest.api;

import com.edgerelative.application.backtest.application.BacktestRunRequest;
import com.edgerelative.application.backtest.application.BacktestRunRow;
import com.edgerelative.application.backtest.application.BacktestService;
import com.edgerelative.application.backtest.domain.BacktestTrade;
import com.edgerelative.application.backtest.domain.EquityPoint;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Broker-neutral backtest API. Reads never start work; simulated activity never reaches a broker. */
@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {

    private final BacktestService service;
    private final com.edgerelative.application.backtest.application.BacktestTimelineService timelineService;

    public BacktestController(
            BacktestService service,
            com.edgerelative.application.backtest.application.BacktestTimelineService timelineService) {
        this.service = service;
        this.timelineService = timelineService;
    }

    @PostMapping
    public BacktestRunRow start(@RequestBody BacktestRunRequest request) {
        return service.start(request);
    }

    @PostMapping("/sweep")
    public List<BacktestRunRow> sweep(
            @RequestBody com.edgerelative.application.backtest.application.BacktestSweepRequest request) {
        return service.sweep(request);
    }

    @GetMapping
    public List<BacktestRunRow> list(@RequestParam(defaultValue = "50") int limit) {
        return service.list(limit);
    }

    @GetMapping("/{runKey}")
    public ResponseEntity<BacktestRunRow> get(@PathVariable String runKey) {
        return service.get(runKey).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{runKey}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String runKey) {
        boolean cancelled = service.cancel(runKey);
        return ResponseEntity.ok(Map.of("cancelled", cancelled));
    }

    @GetMapping("/{runKey}/trades")
    public List<BacktestTrade> trades(
            @PathVariable String runKey,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.trades(runKey, symbol, limit, offset);
    }

    @GetMapping("/{runKey}/equity")
    public List<EquityPoint> equity(@PathVariable String runKey) {
        return service.equity(runKey);
    }

    @GetMapping("/{runKey}/universe")
    public List<BacktestService.BacktestUniverseEntry> universe(@PathVariable String runKey) {
        return service.universe(runKey);
    }

    @GetMapping("/{runKey}/rejections")
    public List<com.edgerelative.application.backtest.domain.BacktestRejection> rejections(@PathVariable String runKey) {
        return service.rejections(runKey);
    }

    @GetMapping("/{runKey}/aggregate")
    public Map<String, Object> aggregate(@PathVariable String runKey) {
        return service.aggregate(runKey);
    }

    @GetMapping("/{runKey}/instruments/{instrumentId}/timeline")
    public com.edgerelative.application.backtest.domain.InstrumentTimeline timeline(
            @PathVariable String runKey, @PathVariable long instrumentId) {
        return timelineService.timeline(runKey, instrumentId);
    }

    @ExceptionHandler(BacktestService.BacktestValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> validation(BacktestService.BacktestValidationException exception) {
        return Map.of("runnable", false, "errors", List.of(exception.getMessage()));
    }
}
