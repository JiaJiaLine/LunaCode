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
        properties.set("task", text("Task to delegate to the sub agent or teammate."));
        properties.set("subagent_type", text("Optional. Defined agent type; when omitted the service may fork or use general-purpose according to feature flags."));
        properties.set("run_in_background", MAPPER.createObjectNode().put("type", "boolean").put("description", "Optional. Launch directly as a background task."));
        properties.set("name", text("Optional teammate name. When present inside a team, name is registered to the spawned agent id."));
        properties.set("team", text("Optional team name. Defaults to the current team."));
        properties.set("role", text("Optional teammate role label."));
        properties.set("backend", text("Optional backend: same_process or terminal."));
        properties.set("planModeRequired", MAPPER.createObjectNode().put("type", "boolean").put("description", "Require Lead approval before this teammate modifies files."));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        root.set("properties", properties);
        root.set("required", MAPPER.createArrayNode().add("task"));
        this.schema = root;
    }

    private ObjectNode text(String description) {
        return MAPPER.createObjectNode().put("type", "string").put("description", description);
    }

    @Override
    public String name() {
        return "Agent";
    }

    @Override
    public String description() {
        return "Delegate a task to an independent sub agent. Inside a team, provide name to spawn a named teammate and register it for SendMessage routing.";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        SubAgentService subAgentService = subAgentServiceSupplier == null ? null : subAgentServiceSupplier.get();
        if (subAgentService == null) {
            return ToolResult.error("Agent tool is not initialized", Map.of("errorType", "agent_tool_not_ready"));
        }
        SubAgentParentContext parentContext = AgentExecutionContextHolder.current();
        AgentToolRequest request = new AgentToolRequest(
                input == null ? "" : input.path("task").asText(""),
                input != null && input.hasNonNull("subagent_type") ? Optional.of(input.path("subagent_type").asText("")) : Optional.empty(),
                input != null && input.path("run_in_background").asBoolean(false),
                optional(input, "name"),
                optional(input, "team"),
                optional(input, "role"),
                optional(input, "backend"),
                input != null && input.path("planModeRequired").asBoolean(false)
        );
        return subAgentService.launchFromTool(request, parentContext);
    }

    private Optional<String> optional(JsonNode input, String field) {
        if (input == null || !input.hasNonNull(field)) {
            return Optional.empty();
        }
        String value = input.path(field).asText("").strip();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
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
            return new ValidationError("missing_task", "Agent requires task");
        }
        if (input.has("subagent_type") && !input.path("subagent_type").isTextual()) {
            return new ValidationError("invalid_subagent_type", "subagent_type must be a string");
        }
        if (input.has("name") && !input.path("name").isTextual()) {
            return new ValidationError("invalid_name", "name must be a string");
        }
        return null;
    }
}