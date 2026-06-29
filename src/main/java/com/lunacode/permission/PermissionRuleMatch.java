package com.lunacode.permission;

import java.util.Objects;

public record PermissionRuleMatch(
        PermissionRule rule,
        PermissionTarget target,
        String reason
) {
    public PermissionRuleMatch {
        rule = Objects.requireNonNull(rule, "rule");
        target = Objects.requireNonNull(target, "target");
        reason = reason == null || reason.isBlank()
                ? rule.effect() + " " + rule.rawRule()
                : reason.strip();
    }
}
