package com.edgerelative.application.reference;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The supported timeframe registry (DD-05 §96).
 *
 * <p>Timeframes are registry/configuration data, not scattered enum assumptions. M1 is the only
 * persisted base; every other entry is derived from it by the shared deterministic aggregator.
 */
public final class TimeframeCatalog {

    private TimeframeCatalog() {
    }

    /** One registered timeframe. Intraday timeframes have a fixed duration; D1/W1 are calendar-based. */
    public record Spec(String code, Integer durationSeconds, boolean calendarBased) {

        public Duration duration() {
            return durationSeconds == null ? null : Duration.ofSeconds(durationSeconds);
        }
    }

    public static final String M1 = "M1";

    private static final List<Spec> SPECS = List.of(
            new Spec("M1", 60, false),
            new Spec("M3", 180, false),
            new Spec("M5", 300, false),
            new Spec("M15", 900, false),
            new Spec("M30", 1800, false),
            new Spec("H1", 3600, false),
            new Spec("H2", 7200, false),
            new Spec("H4", 14400, false),
            new Spec("D1", null, true),
            new Spec("W1", null, true));

    private static final Map<String, Spec> BY_CODE = SPECS.stream()
            .collect(Collectors.toUnmodifiableMap(Spec::code, Function.identity()));

    public static List<Spec> all() {
        return SPECS;
    }

    public static Optional<Spec> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code.trim().toUpperCase()));
    }

    public static Spec require(String code) {
        return find(code)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe: " + code));
    }
}
