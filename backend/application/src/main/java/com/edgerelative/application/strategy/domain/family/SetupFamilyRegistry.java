package com.edgerelative.application.strategy.domain.family;

import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of implemented setup families. Unregistered families are explicitly unsupported. */
public final class SetupFamilyRegistry {

    private final Map<SetupFamily, SetupFamilyDetector> detectors = new EnumMap<>(SetupFamily.class);

    public SetupFamilyRegistry(List<SetupFamilyDetector> detectors) {
        detectors.forEach(detector -> this.detectors.put(detector.family(), detector));
    }

    public static SetupFamilyRegistry production() {
        return new SetupFamilyRegistry(List.of(
                new ThreeEightConfirmationFamily(),
                new CompressionBreakoutFamily(),
                new HorizontalLevelBreakFamily()));
    }

    public Optional<SetupFamilyDetector> detector(SetupFamily family) {
        return Optional.ofNullable(detectors.get(family));
    }

    /** True only for families with an implementation; TRENDLINE_BREAK / PULLBACK_RESUMPTION are not. */
    public boolean supported(SetupFamily family) {
        return detectors.containsKey(family);
    }

    public boolean enabled(SetupFamily family, StrategyParameters parameters) {
        return detector(family).map(detector -> detector.enabled(parameters)).orElse(false);
    }

    public List<SetupFamily> supportedFamilies() {
        return List.copyOf(detectors.keySet());
    }
}
