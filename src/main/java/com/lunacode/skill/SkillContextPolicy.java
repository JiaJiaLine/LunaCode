package com.lunacode.skill;

import java.util.Locale;

public enum SkillContextPolicy {
    FULL,
    RECENT,
    NONE;

    public static SkillContextPolicy fromFrontmatter(String value) {
        if (value == null || value.isBlank()) {
            return FULL;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "full" -> FULL;
            case "recent" -> RECENT;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException("invalid context: " + value);
        };
    }
}
