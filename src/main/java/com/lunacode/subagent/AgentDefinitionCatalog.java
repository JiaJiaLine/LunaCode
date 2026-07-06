package com.lunacode.subagent;

import java.util.List;
import java.util.Optional;

public interface AgentDefinitionCatalog {
    AgentDefinitionCatalogSnapshot snapshot();

    Optional<AgentDefinition> find(String agentType);

    List<AgentDefinitionDiagnostic> diagnostics();
}
