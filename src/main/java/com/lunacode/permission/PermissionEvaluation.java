package com.lunacode.permission;

import java.util.List;
import java.util.Objects;

public record PermissionEvaluation(
        Decision decision,
        PermissionDecisionLayer layer,
        String reason,
        List<PermissionRuleMatch> matches,
        String suggestedAllowRule,
        List<String> warnings
) {
    public PermissionEvaluation {
        decision = Objects.requireNonNull(decision, "decision");
        layer = Objects.requireNonNull(layer, "layer");
        reason = reason == null ? "" : reason;
        matches = matches == null ? List.of() : List.copyOf(matches);
        suggestedAllowRule = suggestedAllowRule == null || suggestedAllowRule.isBlank() ? null : suggestedAllowRule.strip();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static PermissionEvaluation allow(PermissionDecisionLayer layer, String reason, List<PermissionRuleMatch> matches, List<String> warnings) {
        return new PermissionEvaluation(Decision.ALLOW, layer, reason, matches, null, warnings);
    }

    public static PermissionEvaluation ask(PermissionDecisionLayer layer, String reason, String suggestedAllowRule, List<String> warnings) {
        return new PermissionEvaluation(Decision.ASK, layer, reason, List.of(), suggestedAllowRule, warnings);
    }

    public static PermissionEvaluation deny(PermissionDecisionLayer layer, String reason, List<PermissionRuleMatch> matches, List<String> warnings) {
        return new PermissionEvaluation(Decision.DENY, layer, reason, matches, null, warnings);
    }

    public enum Decision {
        ALLOW,
        ASK,
        DENY
    }
}
