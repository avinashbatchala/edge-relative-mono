/**
 * Vendor-neutral LLM ports and models.
 *
 * <p>Framework-free by design, mirroring {@code broker-api}: an {@code llm-*} adapter depends on this
 * module, never the reverse, and no adapter type leaks into the application or the UI. LLM output is
 * advisory narration only and never carries trading authority (DD-06, ADR-007).
 */
package com.edgerelative.llm.api;
