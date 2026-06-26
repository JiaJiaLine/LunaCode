package com.lunacode.tool;

import java.util.Map;

public record ToolResult(
        String content,
        boolean isError,
        Map<String, Object> metadata
) {
    public ToolResult {
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ToolResult success(String content, Map<String, Object> metadata) {
        return new ToolResult(content, false, metadata);
    }

    public static ToolResult error(String content, Map<String, Object> metadata) {
        return new ToolResult(content, true, metadata);
    }
}
