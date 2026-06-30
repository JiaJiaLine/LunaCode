package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinitionSnapshot(
        String name,
        String description,
        JsonNode inputSchema
) {}
