package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.mcp.McpContentRenderer;
import com.lunacode.mcp.McpSession;
import com.lunacode.mcp.McpToolCallResult;
import com.lunacode.mcp.McpToolDefinition;

import java.util.Map;
import java.util.Objects;

public final class McpToolWrapper implements Tool {
    private final McpToolDefinition definition;
    private final McpSession session;
    private final McpContentRenderer renderer;

    public McpToolWrapper(McpToolDefinition definition, McpSession session) {
        this(definition, session, new McpContentRenderer());
    }

    public McpToolWrapper(McpToolDefinition definition, McpSession session, McpContentRenderer renderer) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.session = Objects.requireNonNull(session, "session");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public String name() {
        return definition.publicName();
    }

    @Override
    public String description() {
        return definition.description();
    }

    @Override
    public JsonNode inputSchema() {
        return definition.inputSchema();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        try {
            McpToolCallResult result = session.callTool(definition.originalName(), input, context.commandTimeout()).join();
            return renderer.render(definition.serverName(), definition.originalName(), result, context);
        } catch (Exception e) {
            return ToolResult.error(
                    "MCP 工具调用失败: server=" + definition.serverName() + ", tool=" + definition.originalName() + ", reason=" + e.getMessage(),
                    Map.of("errorType", "mcp_tool_error", "server", definition.serverName(), "remoteTool", definition.originalName())
            );
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isDestructive() {
        return true;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return false;
    }

    @Override
    public String category() {
        return "mcp";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            return new ValidationError("invalid_arguments", "MCP 工具参数必须是 JSON object");
        }
        return null;
    }

    @Override
    public boolean shouldDefer() {
        return true;
    }
}
