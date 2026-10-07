package com.edgerelative.application.ml.api;

import com.edgerelative.application.ml.application.MlVerificationService;
import com.edgerelative.application.ml.application.MlVerificationService.VerificationReport;
import com.edgerelative.application.ml.application.MlVerificationService.VerificationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verification backtest: baseline versus ML ranking overlay over the same window. Read-only and
 * non-authorizing; it persists no run and reaches no broker.
 */
@RestController
@RequestMapping("/api/v1/ml")
public class MlVerificationController {

    private final MlVerificationService service;

    public MlVerificationController(MlVerificationService service) {
        this.service = service;
    }

    @PostMapping("/verify")
    public VerificationReport verify(@RequestBody VerificationRequest request) {
        return service.verify(request);
    }
}
