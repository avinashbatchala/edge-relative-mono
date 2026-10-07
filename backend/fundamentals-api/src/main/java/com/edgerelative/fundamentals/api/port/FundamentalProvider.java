package com.edgerelative.fundamentals.api.port;

import com.edgerelative.fundamentals.api.model.FundamentalRequest;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;

/**
 * A source of company fundamentals for NSE/BSE-listed instruments.
 *
 * <p>Implementations return a point-in-time view: only facts publicly filed on or before the
 * request's {@code asOf} instant. Missing data is reported as absent, never as zero.
 */
public interface FundamentalProvider {

    /** Stable provider identifier, for example {@code yahoo-nse}. Never a credential. */
    String providerName();

    /**
     * Fetches the most recent periods visible as of the request cutoff.
     *
     * @throws com.edgerelative.fundamentals.api.error.FundamentalException on failure
     */
    FundamentalSnapshot fetch(FundamentalRequest request);
}
