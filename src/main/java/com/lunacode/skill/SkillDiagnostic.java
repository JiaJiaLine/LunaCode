package com.lunacode.skill;

public record SkillDiagnostic(
        SkillDiagnosticLevel level,
        String sourceId,
        String message
) {
    public SkillDiagnostic {
        level = level == null ? SkillDiagnosticLevel.WARNING : level;
        sourceId = sourceId == null ? "" : sourceId;
        message = message == null ? "" : message;
    }
}
