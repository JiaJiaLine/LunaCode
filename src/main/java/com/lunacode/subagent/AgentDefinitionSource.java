package com.lunacode.subagent;

import java.nio.file.Path;
import java.util.List;

public interface AgentDefinitionSource {
    List<AgentDefinitionCandidate> discover(Path projectRoot, Path userHome);
}
