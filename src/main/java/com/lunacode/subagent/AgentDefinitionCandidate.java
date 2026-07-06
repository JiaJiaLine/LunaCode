package com.lunacode.subagent;

import java.nio.file.Path;
import java.util.Optional;

public record AgentDefinitionCandidate(
        AgentDefinitionSourceKind source,
        String sourceId,
        Optional<Path> path,
        Optional<String> content
) {
    public AgentDefinitionCandidate {
        source = source == null ? AgentDefinitionSourceKind.BUILTIN : source;
        sourceId = sourceId == null ? "" : sourceId;
        path = path == null ? Optional.empty() : path.map(p -> p.toAbsolutePath().normalize());
        content = content == null ? Optional.empty() : content;
    }

    public static AgentDefinitionCandidate file(AgentDefinitionSourceKind source, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return new AgentDefinitionCandidate(source, normalized.toString(), Optional.of(normalized), Optional.empty());
    }

    public static AgentDefinitionCandidate memory(AgentDefinitionSourceKind source, String sourceId, String content) {
        return new AgentDefinitionCandidate(source, sourceId, Optional.of(Path.of(sourceId == null ? "agent.md" : sourceId)), Optional.ofNullable(content));
    }
}
