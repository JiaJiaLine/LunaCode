package com.lunacode.config;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record FeatureConfig(Set<String> enabled) {
    public FeatureConfig {
        enabled = enabled == null ? Set.of() : enabled.stream()
                .map(FeatureConfig::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static FeatureConfig disabled() {
        return new FeatureConfig(Set.of());
    }

    public static FeatureConfig fromMap(Map<String, Boolean> values) {
        if (values == null || values.isEmpty()) {
            return disabled();
        }
        return new FeatureConfig(values.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));
    }

    public boolean enabled(FeatureGate gate) {
        return gate != null && enabled.contains(normalize(gate.configKey()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
