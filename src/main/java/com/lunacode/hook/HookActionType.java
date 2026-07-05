package com.lunacode.hook;

import java.util.Locale;
import java.util.Optional;

public enum HookActionType {
    COMMAND,
    PROMPT,
    HTTP,
    SUB_AGENT;

    public String yamlName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<HookActionType> fromYamlName(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (HookActionType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
