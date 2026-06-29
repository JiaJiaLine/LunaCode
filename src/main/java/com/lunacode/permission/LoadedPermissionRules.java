package com.lunacode.permission;

import java.util.ArrayList;
import java.util.List;

public record LoadedPermissionRules(
        List<PermissionRule> userRules,
        List<PermissionRule> projectRules,
        List<PermissionRule> localRules,
        List<String> warnings
) {
    public LoadedPermissionRules {
        userRules = userRules == null ? List.of() : List.copyOf(userRules);
        projectRules = projectRules == null ? List.of() : List.copyOf(projectRules);
        localRules = localRules == null ? List.of() : List.copyOf(localRules);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static LoadedPermissionRules empty() {
        return new LoadedPermissionRules(List.of(), List.of(), List.of(), List.of());
    }

    public List<PermissionRule> allRules() {
        List<PermissionRule> rules = new ArrayList<>();
        rules.addAll(userRules);
        rules.addAll(projectRules);
        rules.addAll(localRules);
        return List.copyOf(rules);
    }

    public List<PermissionRule> rulesAt(PermissionRuleLevel level) {
        return switch (level) {
            case USER -> userRules;
            case PROJECT -> projectRules;
            case LOCAL -> localRules;
        };
    }
}
