package com.lunacode.config;

import com.lunacode.permission.PermissionMode;

public record PermissionConfig(
        PermissionMode mode
) {
    public PermissionConfig {
        mode = mode == null ? PermissionMode.DEFAULT : mode;
    }

    public static PermissionConfig defaults() {
        return new PermissionConfig(PermissionMode.DEFAULT);
    }
}
