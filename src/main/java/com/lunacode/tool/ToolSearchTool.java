package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;

public final class ToolSearchTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ToolRegistry registry;
    private final JsonNode schema;

    public ToolSearchTool(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("name", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "要检索的延迟工具公开名称"));
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", properties);
        ((ObjectNode) schema).set("required", MAPPER.createArrayNode().add("name"));
    }

    @Override
    public String name() {
        return "ToolSearch";
    }

    @Override
    public String description() {
        return "按公开工具名检索已注册的延迟工具完整定义。只查询本地工具注册表，不启动或调用远端服务。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String name = input.path("name").asText("");
        return registry.discoverDeferredTool(name)
                .map(snapshot -> ToolResult.success(render(snapshot), Map.of(
                        "toolName", snapshot.name(),
                        "category", "tool_search"
                )))
                .orElseGet(() -> ToolResult.error("未找到可公开的延迟工具: " + name, Map.of(
                        "errorType", "deferred_tool_not_found",
                        "toolName", name
                )));
    }

    private String render(ToolDefinitionSnapshot snapshot) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", snapshot.name());
        node.put("description", snapshot.description());
        node.set("input_schema", snapshot.inputSchema());
        return node.toPrettyString();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isDestructive() {
        return false;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return true;
    }

    @Override
    public String category() {
        return "tool";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("name") || input.path("name").asText().isBlank()) {
            return new ValidationError("missing_name", "ToolSearch 需要 name 参数");
        }
        return null;
    }
}
