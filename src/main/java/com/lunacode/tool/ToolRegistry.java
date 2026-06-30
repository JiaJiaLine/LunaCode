package com.lunacode.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.runtime.AgentMode;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    void register(Tool tool);

    void enable(String name);

    void disable(String name);

    Optional<Tool> get(String name);

    default Optional<Tool> getRegistered(String name) {
        return get(name);
    }

    List<Tool> getEnabledTools();

    default Optional<ToolDefinitionSnapshot> discoverDeferredTool(String name) {
        return Optional.empty();
    }

    default boolean isDeferredDiscovered(String name) {
        return false;
    }

    default List<DeferredToolSummary> deferredToolSummaries() {
        return List.of();
    }

    default ToolDeclarationSet declarationsForModel(AgentMode mode) {
        return new ToolDeclarationSet(toAPIFormat(mode), List.of());
    }

    ArrayNode toAPIFormat();

    default ArrayNode toAPIFormat(AgentMode mode) {
        return toAPIFormat();
    }
}
