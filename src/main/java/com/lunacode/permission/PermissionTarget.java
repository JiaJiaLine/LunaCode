package com.lunacode.permission;

import java.util.Objects;

public record PermissionTarget(
        String toolName,
        PermissionTargetKind kind,
        String value,
        VirtualPath virtualPath,
        boolean sensitive
) {
    public PermissionTarget {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        toolName = toolName.strip();
        kind = Objects.requireNonNull(kind, "kind");
        value = value == null ? "" : value.strip();
    }

    public static PermissionTarget command(String toolName, String command) {
        return new PermissionTarget(toolName, PermissionTargetKind.COMMAND_TEXT, command, null, false);
    }

    public static PermissionTarget path(String toolName, VirtualPath path, boolean sensitive) {
        return new PermissionTarget(toolName, PermissionTargetKind.FILE_PATH, path.virtualPath(), path, sensitive);
    }

    public static PermissionTarget pattern(String toolName, String pattern) {
        return new PermissionTarget(toolName, PermissionTargetKind.SEARCH_PATTERN, pattern, null, false);
    }
}
