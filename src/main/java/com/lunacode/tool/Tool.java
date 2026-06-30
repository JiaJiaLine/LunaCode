package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {
    String name();

    String description();

    JsonNode inputSchema();

    ToolResult execute(ToolExecutionContext context, JsonNode input);

    boolean isReadOnly();

    boolean isDestructive();

    boolean isConcurrencySafe(JsonNode input);

    String category();

    ValidationError validateInput(JsonNode input);

    default boolean shouldDefer() {
        return false;
    }
}
