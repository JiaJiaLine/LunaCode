package com.lunacode.hook;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum HookEventName {
    STARTUP,
    SHUTDOWN,
    SESSION_START,
    SESSION_END,
    TURN_START,
    TURN_END,
    PRE_SEND,
    POST_RECEIVE,
    PRE_TOOL_USE,
    POST_TOOL_USE,
    ERROR,
    COMPACT,
    PERMISSION_REQUEST,
    FILE_CHANGE,
    COMMAND_EXECUTE;

    public String yamlName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<HookEventName> fromYamlName(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
                .filter(event -> event.name().equals(normalized))
                .findFirst();
    }
}
