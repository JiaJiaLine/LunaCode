package com.lunacode.subagent;

import com.lunacode.permission.PermissionMode;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record AgentDefinition(
        String agentType,
        String whenToUse,
        List<String> tools,
        List<String> disallowedTools,
        String model,
        OptionalInt maxTurns,
        Optional<PermissionMode> permissionMode,
        AgentIsolation isolation,
        boolean background,
        String systemPrompt,
        Path filePath,
        AgentDefinitionSourceKind source
) {
    public AgentDefinition(
            String agentType,
            String whenToUse,
            List<String> tools,
            List<String> disallowedTools,
            String model,
            OptionalInt maxTurns,
            Optional<PermissionMode> permissionMode,
            String systemPrompt,
            Path filePath,
            AgentDefinitionSourceKind source
    ) {
        this(agentType, whenToUse, tools, disallowedTools, model, maxTurns, permissionMode, AgentIsolation.NONE, false, systemPrompt, filePath, source);
    }

    public AgentDefinition(
            String agentType,
            String whenToUse,
            List<String> tools,
            List<String> disallowedTools,
            String model,
            OptionalInt maxTurns,
            Optional<PermissionMode> permissionMode,
            boolean background,
            String systemPrompt,
            Path filePath,
            AgentDefinitionSourceKind source
    ) {
        this(agentType, whenToUse, tools, disallowedTools, model, maxTurns, permissionMode, AgentIsolation.NONE, background, systemPrompt, filePath, source);
    }
    public AgentDefinition {
        agentType = requireText(agentType, "agentType");
        whenToUse = requireText(whenToUse, "whenToUse");
        tools = tools == null ? List.of() : tools.stream().map(AgentDefinition::strip).filter(value -> !value.isBlank()).toList();
        disallowedTools = disallowedTools == null ? List.of() : disallowedTools.stream().map(AgentDefinition::strip).filter(value -> !value.isBlank()).toList();
        model = strip(model).isBlank() ? "inherit" : strip(model);
        maxTurns = maxTurns == null ? OptionalInt.empty() : maxTurns;
        permissionMode = permissionMode == null ? Optional.empty() : permissionMode;
        isolation = isolation == null ? AgentIsolation.NONE : isolation;
        systemPrompt = systemPrompt == null ? "" : systemPrompt.strip();
        filePath = Objects.requireNonNull(filePath, "filePath").toAbsolutePath().normalize();
        source = source == null ? AgentDefinitionSourceKind.BUILTIN : source;
    }

    private static String requireText(String value, String field) {
        String stripped = strip(value);
        if (stripped.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return stripped;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}