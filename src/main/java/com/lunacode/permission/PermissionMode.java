package com.lunacode.permission;

import java.util.Locale;

public enum PermissionMode {
    DEFAULT("default"),
    ACCEPT_EDITS("acceptEdits"),
    PLAN("plan"),
    BYPASS_PERMISSIONS("bypassPermissions");

    private final String configValue;

    PermissionMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static PermissionMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.strip();
        for (PermissionMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return mode;
            }
        }
        String compact = normalized.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        return switch (compact) {
            case "default", "ask" -> DEFAULT;
            case "acceptedits", "acceptedit", "edits", "edit" -> ACCEPT_EDITS;
            case "plan" -> PLAN;
            case "bypasspermissions", "bypass", "dangerouslyskippermissions" -> BYPASS_PERMISSIONS;
            default -> valueOf(normalized.replace("-", "_").toUpperCase(Locale.ROOT));
        };
    }
}
