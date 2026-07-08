package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.team.task.TeamTaskRecord;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.Map;

public final class TaskGetTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public TaskGetTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("team", TeamToolSupport.textProperty("Optional team name"));
        properties.set("taskID", TeamToolSupport.textProperty("Task id"));
        this.schema = TeamToolSupport.rootSchema(properties, "taskID");
    }

    @Override public String name() { return "TaskGet"; }
    @Override public String description() { return "Get a shared team task by id."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String teamName = TeamToolSupport.teamName(manager, input);
        TeamTaskRecord task = manager.getTask(teamName, TeamToolSupport.text(input, "taskID"))
                .orElseThrow(() -> new IllegalArgumentException("task not found"));
        return ToolResult.success(detail(task), Map.of("team", teamName, "taskId", task.id()));
    }

    static String detail(TeamTaskRecord task) {
        return TaskCreateTool.format(task)
                + "\ndescription: " + task.description()
                + "\nblockedBy: " + task.blockedBy()
                + "\nblocks: " + task.blocks()
                + "\nclaimable: " + task.claimable();
    }

    @Override public boolean isReadOnly() { return true; }
    @Override public boolean isDestructive() { return false; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return true; }
    @Override public String category() { return "team"; }
    @Override public ValidationError validateInput(JsonNode input) { return TeamToolSupport.require(input, "taskID", "missing_task_id"); }
}
