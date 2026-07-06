package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.subagent.AgentExecutionContextHolder;
import com.lunacode.subagent.AgentToolRequest;
import com.lunacode.subagent.SubAgentParentContext;
import com.lunacode.subagent.SubAgentService;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class AgentTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Supplier<SubAgentService> subAgentServiceSupplier;
    private final JsonNode schema;

    public AgentTool(SubAgentService subAgentService) {
        this(() -> subAgentService);
    }

    public AgentTool(Supplier<SubAgentService> subAgentServiceSupplier) {
        this.subAgentServiceSupplier = subAgentServiceSupplier;
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("task", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "要交给子 Agent 执行的任务描述"));
        properties.set("subagent_type", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "可选。指定时启动定义式子 Agent；不指定时启动 Fork 式子 Agent"));
        properties.set("run_in_background", MAPPER.createObjectNode()
                .put("type", "boolean")
                .put("description", "可选。为 true 时直接以后台任务启动"));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        root.set("properties", properties);
        root.set("required", MAPPER.createArrayNode().add("task"));
        this.schema = root;
    }

    @Override
    public String name() {
        return "Agent";
    }

    @Override
    public String description() {
        return "把任务委派给独立子 Agent。指定 subagent_type 时使用预定义角色；不指定时 Fork 当前对话并后台运行。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        SubAgentService subAgentService = subAgentServiceSupplier == null ? null : subAgentServiceSupplier.get();
        if (subAgentService == null) {
            return ToolResult.error("Agent 工具尚未完成初始化", Map.of("errorType", "agent_tool_not_ready"));
        }
        SubAgentParentContext parentContext = AgentExecutionContextHolder.current();
        AgentToolRequest request = new AgentToolRequest(
                input == null ? "" : input.path("task").asText(""),
                input != null && input.hasNonNull("subagent_type") ? Optional.of(input.path("subagent_type").asText("")) : Optional.empty(),
                input != null && input.path("run_in_background").asBoolean(false)
        );
        return subAgentService.launchFromTool(request, parentContext);
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
        return false;
    }

    @Override
    public String category() {
        return "agent";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("task") || input.path("task").asText().isBlank()) {
            return new ValidationError("missing_task", "Agent 需要 task 参数");
        }
        if (input.has("subagent_type") && !input.path("subagent_type").isTextual()) {
            return new ValidationError("invalid_subagent_type", "subagent_type 必须是字符串");
        }
        return null;
    }
}