package com.edgerelative.application.ml.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One point-in-time ML feature vector. Values are keyed by canonical feature name; a feature absent
 * from the map is treated as missing by the evaluator and follows the model's default direction — it
 * is never coerced to zero. Categorical inputs are one-hot encoded by the builder so every model
 * split is numeric.
 */
public record MlFeatureVector(Map<String, Double> values) {

    public MlFeatureVector {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Double> values = new LinkedHashMap<>();

        public Builder numeric(String name, Double value) {
            if (value != null && !value.isNaN() && !value.isInfinite()) {
                values.put(name, value);
            }
            return this;
        }

        public Builder flag(String name, boolean present) {
            if (present) {
                values.put(name, 1.0);
            }
            return this;
        }

        /** One-hot encodes a categorical value as {@code cat_<key>_<VALUE>} (sanitized). */
        public Builder categorical(String key, String value) {
            if (value != null && !value.isBlank()) {
                values.put("cat_" + key + "_" + sanitize(value), 1.0);
            }
            return this;
        }

        private static String sanitize(String value) {
            return value.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        }

        public MlFeatureVector build() {
            return new MlFeatureVector(values);
        }
    }
}
