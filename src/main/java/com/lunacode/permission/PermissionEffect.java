package com.lunacode.permission;

import java.util.Locale;

public enum PermissionEffect {
    ALLOW,
    DENY;

    public static PermissionEffect fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("effect 不能为空");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ALLOW" -> ALLOW;
            case "DENY" -> DENY;
            default -> throw new IllegalArgumentException("effect 只支持 allow 或 deny: " + value);
        };
    }
}
