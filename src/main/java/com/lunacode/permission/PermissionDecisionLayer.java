package com.lunacode.permission;

public enum PermissionDecisionLayer {
    BLACKLIST,
    SANDBOX,
    RULE_DENY,
    RULE_ALLOW,
    SENSITIVE_PATH,
    MODE_POLICY,
    TOOL_NOT_FOUND
}
