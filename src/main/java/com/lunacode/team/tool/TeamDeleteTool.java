package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.Map;

public final class TeamDeleteTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public TeamDeleteTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("team", TeamToolSupport.textProperty("Team name"));
        properties.set("force", TeamToolSupport.MAPPER.createObjectNode().put("type", "boolean"));
        this.schema = TeamToolSupport.rootSchema(properties, "team");
    }

    @Override public String name() { return "TeamDelete"; }
    @Override public String description() { return "Delete a managed team after safety checks."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String message = manager.deleteTeam(TeamToolSupport.text(input, "team"), input != null && input.path("force").asBoolean(false));
        return ToolResult.success(message, Map.of("deleted", true));
    }

    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isDestructive() { return true; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
    @Override public String category() { return "team"; }
    @Override public ValidationError validateInput(JsonNode input) { return TeamToolSupport.require(input, "team", "missing_team"); }
}
