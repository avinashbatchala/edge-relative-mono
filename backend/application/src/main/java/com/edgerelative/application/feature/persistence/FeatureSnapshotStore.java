package com.edgerelative.application.feature.persistence;

import com.edgerelative.application.feature.domain.FeatureSnapshot;

/**
 * Append-only persistence boundary for derived feature snapshots (DD-05 §38/§39).
 */
public interface FeatureSnapshotStore {

    /**
     * @return true when a new snapshot was written; false when an identical one already existed.
     */
    boolean save(FeatureSnapshot snapshot, long timeframeId, String sourceRevision);
}
