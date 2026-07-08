package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.team.task.TaskUpdatePatch;
import com.lunacode.team.task.TeamTaskRecord;
import com.lunacode.team.task.TeamTaskStatus;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TaskUpdateTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public TaskUpdateTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("team", TeamToolSupport.textProperty("Optional team name"));
        properties.set("taskID", TeamToolSupport.textProperty("Task id"));
        properties.set("status", TeamToolSupport.textProperty("Optional TODO, IN_PROGRESS, DONE, CANCELLED"));
        properties.set("assignee", TeamToolSupport.textProperty("Optional assignee"));
        properties.set("clearAssignee", TeamToolSupport.MAPPER.createObjectNode().put("type", "boolean"));
        properties.set("addBlocks", TeamToolSupport.MAPPER.createObjectNode().put("type", "array").set("items", TeamToolSupport.textProperty("Task id")));
        properties.set("addBlockedBy", TeamToolSupport.MAPPER.createObjectNode().put("type", "array").set("items", TeamToolSupport.textProperty("Task id")));
        properties.set("claim", TeamToolSupport.MAPPER.createObjectNode().put("type", "boolean"));
        properties.set("actor", TeamToolSupport.textProperty("Optional actor for claim"));
        this.schema = TeamToolSupport.rootSchema(properties, "taskID");
    }

    @Override public String name() { return "TaskUpdate"; }
    @Override public String description() { return "Update task status, assignee, claim state, and addBlocks/addBlockedBy dependencies."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String teamName = TeamToolSupport.teamName(manager, input);
        TaskUpdatePatch patch = new TaskUpdatePatch(
                status(input),
                TeamToolSupport.optionalText(input, "assignee"),
                input != null && input.path("clearAssignee").asBoolean(false),
                stringArray(input, "addBlocks"),
                stringArray(input, "addBlockedBy"),
                input != null && input.path("claim").asBoolean(false)
        );
        TeamTaskRecord task = manager.updateTask(teamName, TeamToolSupport.text(input, "taskID"), patch, TeamToolSupport.actor(input));
        return ToolResult.success(TaskGetTool.detail(task), Map.of("team", teamName, "taskId", task.id()));
    }

    private Optional<TeamTaskStatus> status(JsonNode input) {
        String value = TeamToolSupport.text(input, "status");
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(TeamTaskStatus.valueOf(value.toUpperCase()));
    }

    private Set<String> stringArray(JsonNode input, String field) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JsonNode node = input == null ? null : input.path(field);
        if (node != null && node.isArray()) {
            node.forEach(value -> {
                if (value.isTextual() && !value.asText().isBlank()) {
                    values.add(value.asText());
                }
            });
        }
        return Set.copyOf(values);
    }

    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isDestructive() { return false; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
    @Override public String category() { return "team"; }
    @Override public ValidationError validateInput(JsonNode input) { return TeamToolSupport.require(input, "taskID", "missing_task_id"); }
}
