package com.edgerelative.application.ml.api;

import com.edgerelative.application.ml.application.MlTrainingExportService;
import com.edgerelative.application.ml.application.MlTrainingExportService.AnchorRow;
import com.edgerelative.application.ml.application.MlTrainingExportService.ExportRequest;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-anchor training export. Replays the production engine over the requested window and returns the
 * exact feature vectors plus realized outcomes the research trainer consumes. Read-only: it starts no
 * run, writes nothing and reaches no broker.
 */
@RestController
@RequestMapping("/api/v1/ml/export")
public class MlTrainingExportController {

    private final MlTrainingExportService service;

    public MlTrainingExportController(MlTrainingExportService service) {
        this.service = service;
    }

    @PostMapping("/anchors")
    public List<AnchorRow> anchors(@RequestBody ExportRequest request) {
        return service.export(request);
    }
}
