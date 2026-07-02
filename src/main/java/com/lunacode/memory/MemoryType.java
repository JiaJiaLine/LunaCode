package com.lunacode.memory;

import java.util.Locale;

public enum MemoryType {
    USER_PREFERENCE("user_preference", true),
    CORRECTION_FEEDBACK("correction_feedback", true),
    PROJECT_KNOWLEDGE("project_knowledge", false),
    REFERENCE_INFO("reference_info", false);

    private final String value;
    private final boolean userLevel;

    MemoryType(String value, boolean userLevel) {
        this.value = value;
        this.userLevel = userLevel;
    }

    public String value() {
        return value;
    }

    public boolean userLevel() {
        return userLevel;
    }

    public static MemoryType fromValue(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        for (MemoryType type : values()) {
            if (type.value.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知记忆类型: " + value);
    }
}
