package com.edgerelative.application.opportunity.api;

import com.edgerelative.application.opportunity.application.OpportunityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only Opportunities workspace. Never creates a plan or a decision. */
@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {

    private final OpportunityService service;

    public OpportunityController(OpportunityService service) {
        this.service = service;
    }

    @GetMapping
    public List<OpportunityRow> list() {
        return service.list();
    }
}
