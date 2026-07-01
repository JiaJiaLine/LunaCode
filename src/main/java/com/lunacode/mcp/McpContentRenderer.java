package com.lunacode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

public final class McpContentRenderer {
    private static final int MAX_REMOTE_TEXT_CHARS = 12_000;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolResult render(String serverName, String originalToolName, McpToolCallResult result, ToolExecutionContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("server", serverName);
        metadata.put("remoteTool", originalToolName);
        if (result.protocolError()) {
            metadata.put("errorType", "mcp_protocol_error");
            return ToolResult.error("MCP 工具调用失败: " + safe(result.failureReason()), metadata);
        }
        String content = renderResult(result.result());
        String masked = context == null ? content : context.masker().mask(content);
        int max = context == null ? MAX_REMOTE_TEXT_CHARS : Math.min(context.maxContentChars(), MAX_REMOTE_TEXT_CHARS);
        String limited = limitContent(masked, max);
        metadata.put("truncated", limited.length() < masked.length());
        if (result.remoteError()) {
            metadata.put("errorType", "mcp_tool_error");
            return ToolResult.error(limited.isBlank() ? "MCP 远端工具返回错误" : limited, metadata);
        }
        return ToolResult.success(limited, metadata);
    }

    private String renderResult(JsonNode result) {
        if (result == null || result.isMissingNode() || result.isNull()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        JsonNode content = result.path("content");
        if (content.isArray()) {
            int nonTextCount = 0;
            for (JsonNode item : content) {
                String type = item.path("type").asText("unknown");
                if ("text".equals(type) && item.has("text")) {
                    builder.append(item.path("text").asText()).append('\n');
                } else {
                    nonTextCount++;
                    builder.append("非文本内容: type=").append(type);
                    String name = item.path("name").asText("");
                    if (!name.isBlank()) {
                        builder.append(", name=").append(limit(name, 80));
                    }
                    builder.append('\n');
                }
            }
            if (nonTextCount > 0) {
                builder.append("非文本内容数量: ").append(nonTextCount).append('\n');
            }
        }
        JsonNode structured = result.path("structuredContent");
        if (!structured.isMissingNode() && !structured.isNull()) {
            builder.append("结构化内容摘要:\n").append(limit(compactJson(structured), 4_000)).append('\n');
        }
        if (builder.isEmpty()) {
            builder.append(limit(compactJson(result), 4_000));
        }
        return builder.toString().strip();
    }

    private String compactJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return node == null ? "" : node.toString();
        }
    }

    private String limit(String value, int maxChars) {
        String safe = safe(value);
        return safe.length() <= maxChars ? safe : safe.substring(0, Math.max(0, maxChars)) + "...";
    }

    private String limitContent(String value, int maxChars) {
        String safe = safe(value);
        return safe.length() <= maxChars ? safe : safe.substring(0, Math.max(0, maxChars)) + "\n[输出已截断]";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
