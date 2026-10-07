/**
 * Broker-neutral fundamental data ports and models.
 *
 * <p>Fundamentals are advisory, periodically-reported reference data (DD-06). They never grant
 * trading authority. Adapters (for example {@code fundamentals-nse}) depend on this module, never
 * the reverse; provider types stay in the adapter.
 */
package com.edgerelative.fundamentals.api;
