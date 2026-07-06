package com.lunacode.subagent;

public record AgentDefinitionDiagnostic(
        DiagnosticLevel level,
        String sourceId,
        String message
) {
    public AgentDefinitionDiagnostic {
        level = level == null ? DiagnosticLevel.WARNING : level;
        sourceId = sourceId == null ? "" : sourceId;
        message = message == null ? "" : message;
    }
}
