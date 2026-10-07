package com.edgerelative.application.fundamental.api;

import com.edgerelative.application.fundamental.application.FundamentalService;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral point-in-time fundamental read API (DD-06). Depends only on the application
 * service; no provider type is exposed.
 */
@RestController
@RequestMapping("/api/v1/fundamentals")
public class FundamentalController {

    private final FundamentalService service;

    public FundamentalController(FundamentalService service) {
        this.service = service;
    }

    /**
     * Returns the fundamentals visible at {@code asOf}. {@code refresh=true} fetches from the
     * provider and appends a new observation first.
     */
    @GetMapping("/{instrumentId}")
    public FundamentalResponse get(
            @PathVariable long instrumentId,
            @RequestParam Instant asOf,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return FundamentalResponse.from(
                instrumentId, refresh ? service.refresh(instrumentId, asOf) : service.latest(instrumentId, asOf));
    }
}
