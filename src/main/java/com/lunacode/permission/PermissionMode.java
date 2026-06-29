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
        String upper = normalized.replace("-", "_").toUpperCase(Locale.ROOT);
        if ("ACCEPTEDITS".equals(upper)) {
            return ACCEPT_EDITS;
        }
        if ("BYPASSPERMISSIONS".equals(upper)) {
            return BYPASS_PERMISSIONS;
        }
        return valueOf(upper);
    }
}
