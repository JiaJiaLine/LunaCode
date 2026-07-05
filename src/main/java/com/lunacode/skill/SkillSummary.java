package com.lunacode.skill;

public record SkillSummary(
        String name,
        String description,
        SkillOrigin origin,
        SkillExecutionMode mode
) {
    public SkillSummary {
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        mode = mode == null ? SkillExecutionMode.INLINE : mode;
    }
}
