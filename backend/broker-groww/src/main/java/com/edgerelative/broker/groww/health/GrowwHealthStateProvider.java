package com.edgerelative.broker.groww.health;

/** Supplies the current Groww adapter health to the application's health endpoint. */
public interface GrowwHealthStateProvider {

    GrowwHealth health();
}
