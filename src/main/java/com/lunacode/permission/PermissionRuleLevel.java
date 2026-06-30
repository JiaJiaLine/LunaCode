package com.lunacode.permission;

public enum PermissionRuleLevel {
    USER(1),
    PROJECT(2),
    LOCAL(3);

    private final int priority;

    PermissionRuleLevel(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
