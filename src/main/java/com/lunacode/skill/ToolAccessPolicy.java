package com.lunacode.skill;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record ToolAccessPolicy(
        Optional<Set<String>> allowedTools,
        Set<String> alwaysVisibleTools,
        Set<String> deniedTools
) {
    public ToolAccessPolicy(Optional<Set<String>> allowedTools, Set<String> alwaysVisibleTools) {
        this(allowedTools, alwaysVisibleTools, Set.of());
    }

    public ToolAccessPolicy {
        allowedTools = allowedTools == null ? Optional.empty() : allowedTools.map(ToolAccessPolicy::normalizeSet);
        alwaysVisibleTools = alwaysVisibleTools == null ? Set.of() : normalizeSet(alwaysVisibleTools);
        deniedTools = deniedTools == null ? Set.of() : normalizeSet(deniedTools);
    }

    public static ToolAccessPolicy unrestricted(Set<String> alwaysVisibleTools) {
        return new ToolAccessPolicy(Optional.empty(), alwaysVisibleTools, Set.of());
    }

    public static ToolAccessPolicy restricted(Set<String> allowedTools, Set<String> alwaysVisibleTools) {
        return new ToolAccessPolicy(Optional.of(allowedTools == null ? Set.of() : allowedTools), alwaysVisibleTools, Set.of());
    }

    public ToolAccessPolicy withDeniedTools(Set<String> deniedTools) {
        return new ToolAccessPolicy(allowedTools, alwaysVisibleTools, deniedTools);
    }

    public boolean isRestricted() {
        return allowedTools.isPresent();
    }

    public boolean allows(String toolName) {
        String normalized = normalize(toolName);
        if (deniedTools.contains(normalized)) {
            return false;
        }
        if (alwaysVisibleTools.contains(normalized)) {
            return true;
        }
        return allowedTools.map(tools -> tools.contains(normalized)).orElse(true);
    }

    private static Set<String> normalizeSet(Set<String> values) {
        return values.stream()
                .map(ToolAccessPolicy::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
