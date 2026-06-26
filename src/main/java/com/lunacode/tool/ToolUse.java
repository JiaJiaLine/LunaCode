package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolUse(
        String id,
        String name,
        JsonNode input
) {}
