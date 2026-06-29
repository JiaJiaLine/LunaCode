package com.lunacode.permission;

import java.nio.file.Path;

public final class PermissionRuleParser {
    public PermissionRule parse(
            String rawRule,
            String rawEffect,
            PermissionRuleLevel level,
            int order,
            Path source
    ) {
        if (rawRule == null || rawRule.isBlank()) {
            throw new IllegalArgumentException("rule 不能为空");
        }
        String normalized = rawRule.strip();
        int open = normalized.indexOf('(');
        int close = normalized.lastIndexOf(')');
        if (open <= 0 || close != normalized.length() - 1 || close <= open) {
            throw new IllegalArgumentException("rule 必须是 Tool(pattern) 格式: " + rawRule);
        }
        String toolName = normalized.substring(0, open).strip();
        String pattern = normalized.substring(open + 1, close);
        if (toolName.isBlank()) {
            throw new IllegalArgumentException("rule 工具名不能为空: " + rawRule);
        }
        if (pattern.isBlank()) {
            throw new IllegalArgumentException("rule 匹配模式不能为空: " + rawRule);
        }
        return new PermissionRule(
                normalized,
                toolName,
                pattern,
                PermissionEffect.fromConfig(rawEffect),
                level,
                order,
                source
        );
    }
}
