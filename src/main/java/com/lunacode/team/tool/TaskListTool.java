package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.team.task.TaskListFilter;
import com.lunacode.team.task.TeamTaskStatus;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class TaskListTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public TaskListTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("team", TeamToolSupport.textProperty("Optional team name"));
        properties.set("status", TeamToolSupport.textProperty("Optional TODO, IN_PROGRESS, DONE, CANCELLED"));
        properties.set("assignee", TeamToolSupport.textProperty("Optional assignee"));
        properties.set("claimable", TeamToolSupport.MAPPER.createObjectNode().put("type", "boolean"));
        this.schema = TeamToolSupport.rootSchema(properties);
    }

    @Override public String name() { return "TaskList"; }
    @Override public String description() { return "List shared team tasks and dependency state."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String teamName = TeamToolSupport.teamName(manager, input);
        TaskListFilter filter = new TaskListFilter(status(input), TeamToolSupport.optionalText(input, "assignee"), input != null && input.path("claimable").asBoolean(false));
        String content = manager.listTasks(teamName, filter).stream()
                .map(TaskCreateTool::format)
                .collect(Collectors.joining("\n"));
        return ToolResult.success(content.isBlank() ? "no tasks" : content, Map.of("team", teamName));
    }

    private Optional<TeamTaskStatus> status(JsonNode input) {
        String value = TeamToolSupport.text(input, "status");
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(TeamTaskStatus.valueOf(value.toUpperCase()));
    }

    @Override public boolean isReadOnly() { return true; }
    @Override public boolean isDestructive() { return false; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return true; }
    @Override public String category() { return "team"; }
    @Override public ValidationError validateInput(JsonNode input) { return null; }
}
