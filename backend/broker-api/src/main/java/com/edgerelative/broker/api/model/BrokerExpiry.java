package com.edgerelative.broker.api.model;

import java.time.LocalDate;

/** Available derivative expiry for an underlying. */
public record BrokerExpiry(LocalDate expiryDate) {
}
