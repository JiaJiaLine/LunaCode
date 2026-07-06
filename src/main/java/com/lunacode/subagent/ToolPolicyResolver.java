package com.lunacode.subagent;

import com.lunacode.skill.ToolAccessPolicy;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolRegistry;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ToolPolicyResolver {
    private final ToolRegistry registry;

    public ToolPolicyResolver(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolAccessPolicy resolve(ToolAccessPolicy parentPolicy, AgentDefinition definition, boolean backgroundScope, boolean forkScope) {
        Set<String> availableTools = availableTools();
        Set<String> alwaysVisible = parentPolicy == null ? Set.of() : parentPolicy.alwaysVisibleTools();
        Optional<Set<String>> allowed;
        if (definition != null && !definition.tools().isEmpty()) {
            allowed = Optional.of(definition.tools().stream()
                    .map(this::canonicalToolName)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        } else if (parentPolicy != null && parentPolicy.allowedTools().isPresent()) {
            allowed = Optional.of(parentPolicy.allowedTools().orElse(Set.of()));
        } else {
            allowed = Optional.empty();
        }

        Set<String> denied = new LinkedHashSet<>();
        if (parentPolicy != null) {
            denied.addAll(parentPolicy.deniedTools());
        }
        if (definition != null) {
            definition.disallowedTools().stream()
                    .map(this::canonicalToolName)
                    .filter(value -> !value.isBlank())
                    .forEach(denied::add);
        }
        if (backgroundScope || forkScope) {
            denied.add(canonicalToolName("Agent"));
        }
        denied.retainAll(withAgent(availableTools));
        return new ToolAccessPolicy(allowed, alwaysVisible, denied);
    }

    private Set<String> availableTools() {
        if (registry == null) {
            return Set.of("agent", "readfile", "writefile", "editfile", "bash", "glob", "grep", "askuserquestion", "loadskill");
        }
        Set<String> names = registry.getEnabledTools().stream()
                .map(Tool::name)
                .map(this::canonicalToolName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return withAgent(names);
    }

    private Set<String> withAgent(Set<String> names) {
        Set<String> result = new LinkedHashSet<>(names);
        result.add("agent");
        return result;
    }

    private String canonicalToolName(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "read" -> "readfile";
            case "write" -> "writefile";
            case "edit" -> "editfile";
            default -> normalized;
        };
    }
}
