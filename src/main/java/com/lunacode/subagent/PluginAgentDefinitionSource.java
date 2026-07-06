package com.lunacode.subagent;

import java.nio.file.Path;
import java.util.List;

public final class PluginAgentDefinitionSource implements AgentDefinitionSource {
    @Override
    public List<AgentDefinitionCandidate> discover(Path projectRoot, Path userHome) {
        return List.of();
    }
}
