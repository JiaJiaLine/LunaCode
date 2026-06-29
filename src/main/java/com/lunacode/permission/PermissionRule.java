package com.lunacode.permission;

import java.nio.file.Path;
import java.util.Objects;

public record PermissionRule(
        String rawRule,
        String toolName,
        String pattern,
        PermissionEffect effect,
        PermissionRuleLevel level,
        int order,
        Path source
) {
    public PermissionRule {
        rawRule = requireText(rawRule, "rawRule");
        toolName = requireText(toolName, "toolName");
        pattern = Objects.requireNonNull(pattern, "pattern");
        effect = Objects.requireNonNull(effect, "effect");
        level = Objects.requireNonNull(level, "level");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.strip();
    }
}
