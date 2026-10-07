package com.edgerelative.application.fundamental.persistence;

import com.edgerelative.application.fundamental.domain.InstrumentRef;
import com.edgerelative.fundamentals.api.model.FundamentalSnapshot;

import java.time.Instant;
import java.util.Optional;

/**
 * Point-in-time persistence for the fundamental store (ADR-005). Writes are append-only; a
 * restatement is a new revision, never an update.
 */
public interface FundamentalStore {

    Optional<InstrumentRef> resolveInstrument(long instrumentId);

    void save(long instrumentId, FundamentalSnapshot snapshot);

    /** The latest reporting period visible at or before {@code asOf}. */
    Optional<FundamentalSnapshot> findLatest(long instrumentId, Instant asOf);
}
