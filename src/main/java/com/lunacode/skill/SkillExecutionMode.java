package com.lunacode.skill;

import java.util.Locale;

public enum SkillExecutionMode {
    INLINE,
    FORK;

    public static SkillExecutionMode fromFrontmatter(String value) {
        if (value == null || value.isBlank()) {
            return INLINE;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "inline" -> INLINE;
            case "fork" -> FORK;
            default -> throw new IllegalArgumentException("invalid mode: " + value);
        };
    }
}
