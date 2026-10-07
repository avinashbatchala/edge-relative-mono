package com.edgerelative.application.fundamental.application;

import com.edgerelative.application.fundamental.domain.InstrumentRef;
import com.edgerelative.application.fundamental.persistence.FundamentalStore;
import com.edgerelative.fundamentals.api.error.FundamentalNotFoundException;
import com.edgerelative.fundamentals.api.error.FundamentalUnavailableException;
import com.edgerelative.fundamentals.api.model.FundamentalRequest;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;
import com.edgerelative.fundamentals.api.model.ReportingBasis;
import com.edgerelative.fundamentals.api.port.FundamentalProvider;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Point-in-time fundamental reads and refreshes.
 *
 * <p>Advisory context only: it never returns a value published after the requested {@code asOf}
 * cutoff, and it never influences setup validity, sizing, or risk (DD-06 §2).
 */
@Service
public class FundamentalService {

    private final ObjectProvider<FundamentalProvider> providerProvider;
    private final FundamentalStore store;

    public FundamentalService(
            ObjectProvider<FundamentalProvider> providerProvider, FundamentalStore store) {
        this.providerProvider = providerProvider;
        this.store = store;
    }

    public FundamentalSnapshot latest(long instrumentId, Instant asOf) {
        return store.findLatest(instrumentId, asOf)
                .orElseThrow(() -> new FundamentalNotFoundException(
                        "No fundamentals recorded for instrument " + instrumentId,
                        "store",
                        "latest",
                        null,
                        null));
    }

    /**
     * Fetches from the provider and appends the result, then returns the point-in-time view. A
     * provider that is not configured yields an unavailable error rather than failing the app.
     */
    public FundamentalSnapshot refresh(long instrumentId, Instant asOf) {
        FundamentalProvider provider = providerProvider.getIfAvailable();
        if (provider == null) {
            throw new FundamentalUnavailableException(
                    "No fundamental provider is configured", "none", "refresh", null, null, null);
        }
        InstrumentRef ref = store.resolveInstrument(instrumentId)
                .orElseThrow(() -> new FundamentalNotFoundException(
                        "Unknown instrument " + instrumentId, provider.providerName(), "refresh", null, null));
        FundamentalSnapshot fetched = provider.fetch(
                new FundamentalRequest(ref.exchange(), ref.symbol(), asOf, ReportingBasis.CONSOLIDATED));
        store.save(instrumentId, fetched);
        return store.findLatest(instrumentId, asOf).orElse(fetched);
    }
}
