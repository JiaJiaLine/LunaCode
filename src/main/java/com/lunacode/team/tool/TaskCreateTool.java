package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.team.task.TaskCreateRequest;
import com.lunacode.team.task.TeamTaskRecord;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.Map;

public final class TaskCreateTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public TaskCreateTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("team", TeamToolSupport.textProperty("Optional team name"));
        properties.set("title", TeamToolSupport.textProperty("Task title"));
        properties.set("description", TeamToolSupport.textProperty("Task description"));
        properties.set("assignee", TeamToolSupport.textProperty("Optional teammate name"));
        this.schema = TeamToolSupport.rootSchema(properties, "title");
    }

    @Override public String name() { return "TaskCreate"; }
    @Override public String description() { return "Create a shared team task with optional assignee."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String teamName = TeamToolSupport.teamName(manager, input);
        TeamTaskRecord task = manager.createTask(teamName, new TaskCreateRequest(
                TeamToolSupport.text(input, "title"),
                TeamToolSupport.text(input, "description"),
                TeamToolSupport.optionalText(input, "assignee")
        ));
        return ToolResult.success(format(task), Map.of("team", teamName, "taskId", task.id()));
    }

    static String format(TeamTaskRecord task) {
        return "T" + task.id() + " [" + task.status() + "] " + task.title()
                + task.assignee().map(value -> " @" + value).orElse("")
                + (task.blocked() ? " blockedBy=" + task.blockedBy() : "");
    }

    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isDestructive() { return false; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
    @Override public String category() { return "team"; }
    @Override public ValidationError validateInput(JsonNode input) { return TeamToolSupport.require(input, "title", "missing_title"); }
}
