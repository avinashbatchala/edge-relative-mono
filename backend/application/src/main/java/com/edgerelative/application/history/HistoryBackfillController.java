package com.edgerelative.application.history;

import com.edgerelative.application.history.api.BackfillRunResponse;
import com.edgerelative.application.history.api.StartBackfillRequest;
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

/** Ingestion API: start/resume M1 downloads and inspect or retry ingestion runs. */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryBackfillController {

    private final HistoricalBackfillService service;

    public HistoryBackfillController(HistoricalBackfillService service) {
        this.service = service;
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
}
