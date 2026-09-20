package com.edgerelative.application.feature.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Immutable feature semantic version plus the exact parameters that produced it (DD-05 §120–§122).
 *
 * <p>{@code semanticVersion} identifies meaning (e.g. {@code RRS_V1}); {@code calculationVersion}
 * identifies an implementation refactor that must not change meaning. {@code parameterHash} makes two
 * different parameter sets distinguishable even under the same semantic name, so historical
 * {@code RRS_V1} rows can never be silently reinterpreted (DD-05 §122, DD-04 configuration rules).
 */
public record FeatureVersion(
        String featureKey,
        String semanticVersion,
        String calculationVersion,
        Map<String, String> parameters,
        String parameterHash) {

    public FeatureVersion {
        parameters = Map.copyOf(new TreeMap<>(parameters));
    }

    public static FeatureVersion of(
            String featureKey,
            String semanticVersion,
            String calculationVersion,
            Map<String, ?> parameters) {
        Map<String, String> normalized = new TreeMap<>();
        parameters.forEach((key, value) -> normalized.put(key, String.valueOf(value)));
        return new FeatureVersion(
                featureKey,
                semanticVersion,
                calculationVersion,
                normalized,
                hash(normalized));
    }

    /**
     * Stable, human-readable identity including the parameter set.
     */
    public String displayVersion() {
        return semanticVersion + "@" + parameterHash;
    }

    public String code() {
        return featureKey + ":" + semanticVersion;
    }

    private static String hash(Map<String, String> parameters) {
        String canonical = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /**
     * Convenience builder that preserves insertion order for readability in tests.
     */
    public static Map<String, Object> parameters(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }
}
