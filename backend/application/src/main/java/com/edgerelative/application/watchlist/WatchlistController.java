package com.edgerelative.application.watchlist;

import com.edgerelative.application.watchlist.api.AddWatchlistItemRequest;
import com.edgerelative.application.watchlist.api.ReorderWatchlistRequest;
import com.edgerelative.application.watchlist.api.WatchlistEntry;
import com.edgerelative.application.watchlist.api.WatchlistResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral watchlist API.
 *
 * <p>The watchlist stores canonical instrument identity; broker tokens are resolved behind the
 * market-data/broker abstraction and never form the identity.
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService service;

    public WatchlistController(WatchlistService service) {
        this.service = service;
    }

    @GetMapping
    public WatchlistResponse watchlist() {
        return service.list();
    }

    @PostMapping("/items")
    public ResponseEntity<WatchlistEntry> add(@RequestBody AddWatchlistItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(request));
    }

    @DeleteMapping("/items/{instrumentId}")
    public ResponseEntity<Void> remove(@PathVariable long instrumentId) {
        service.remove(instrumentId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public WatchlistResponse reorder(@RequestBody ReorderWatchlistRequest request) {
        return service.reorder(request.instrumentIds());
    }
}
