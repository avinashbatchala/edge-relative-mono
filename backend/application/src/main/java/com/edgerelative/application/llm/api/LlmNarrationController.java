package com.edgerelative.application.llm.api;

import com.edgerelative.application.llm.application.LlmNarrationService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Advisory narration API. The API key stays server-side; the browser and the macOS app call this
 * endpoint and never hold a provider credential (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/llm")
public class LlmNarrationController {

    private final LlmNarrationService service;

    public LlmNarrationController(LlmNarrationService service) {
        this.service = service;
    }

    @PostMapping("/narration")
    public LlmNarrationResponse narrate(@RequestBody LlmNarrationRequest request) {
        return LlmNarrationResponse.from(service.narrate(request.question(), request.facts()));
    }
}
