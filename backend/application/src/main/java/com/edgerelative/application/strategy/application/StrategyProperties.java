package com.edgerelative.application.strategy.application;

import com.edgerelative.application.strategy.domain.SetupFamily;
import com.edgerelative.application.strategy.domain.StrategyParameters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed strategy configuration. The engine is disabled unless every required research parameter is
 * supplied; there are no invented defaults for trading thresholds (DD-02 leaves them to research).
 * Enabling requires {@code strategy.er-rs-continuation-v1.enabled=true} plus the parameter map.
 */
@ConfigurationProperties(prefix = "strategy.er-rs-continuation-v1")
public class StrategyProperties {

    private boolean enabled = false;
    private List<String> enabledFamilies = new ArrayList<>();
    private Map<String, String> parameters = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getEnabledFamilies() {
        return enabledFamilies;
    }

    public void setEnabledFamilies(List<String> enabledFamilies) {
        this.enabledFamilies = enabledFamilies;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    /** Builds the immutable parameter set, failing fast if a required value is missing. */
    public Optional<StrategyParameters> toParameters() {
        if (!enabled) {
            return Optional.empty();
        }
        Set<SetupFamily> families = enabledFamilies.stream()
                .map(SetupFamily::parse)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return Optional.of(new StrategyParameters(
                require("parameterSetId"),
                integer("parameterVersion"),
                families,
                number("rrsM5PersistenceLongMin"),
                number("rrsM5PersistenceShortMin"),
                number("minRvolDaily"),
                number("minRvolInterval"),
                number("minRvolCumulative"),
                number("minLiquidityMedianTradedValue"),
                number("minTechnicalVoidAtr"),
                number("maxEntryExtensionAtr"),
                number("nearTriggerDistanceAtr"),
                number("triggerBufferAtrFraction"),
                integer("triggerBufferTicks"),
                integer("maxBarsSinceTrigger"),
                integer("maxBarsInMainState"),
                integer("openingBlackoutMinutes"),
                integer("entryCutoffMinutesBeforeClose"),
                number("dataStalenessSeconds"),
                StrategyParameters.NeutralMarketPolicy.valueOf(
                        parameters.getOrDefault("neutralMarketPolicy", "BLOCK")),
                number("neutralRrsPersistenceExtra"),
                optionalNumber("compressionMaxRangeAtr"),
                optionalNumber("compressionMaxEfficiency"),
                optionalNumber("compressionMinOverlap"),
                optionalInteger("horizontalPivotWidth"),
                optionalNumber("horizontalToleranceAtr")));
    }

    private String require(String key) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("strategy.er-rs-continuation-v1.parameters." + key + " is required");
        }
        return value;
    }

    private double number(String key) {
        return Double.parseDouble(require(key));
    }

    private int integer(String key) {
        return Integer.parseInt(require(key));
    }

    private Double optionalNumber(String key) {
        String value = parameters.get(key);
        return value == null || value.isBlank() ? null : Double.valueOf(value);
    }

    private Integer optionalInteger(String key) {
        String value = parameters.get(key);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }
}
