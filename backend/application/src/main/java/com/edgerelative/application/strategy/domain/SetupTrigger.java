package com.edgerelative.application.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Confirmed entry trigger. {@code triggerDistance} is signed relative to the trigger level;
 * {@code entryExtensionAtr} is {@code abs(currentPrice - triggerLevel) / ATR_M5} (DD-02 §68/§12).
 */
public record SetupTrigger(
        String triggerType,
        BigDecimal triggerLevel,
        BigDecimal triggerBuffer,
        BigDecimal confirmationPrice,
        Instant confirmationBarOpenTime,
        Instant confirmationBarCloseTime,
        String confirmationObservationId,
        BigDecimal triggerDistance,
        Double entryExtensionAtr) {
}
