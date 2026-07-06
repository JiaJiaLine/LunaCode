package com.lunacode.subagent;

import java.util.List;

public record AgentDefinitionCatalogSnapshot(
        List<AgentDefinition> definitions,
        List<AgentDefinitionDiagnostic> diagnostics
) {
    public AgentDefinitionCatalogSnapshot {
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
