package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.team.TeamRecord;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.Map;

public final class TeamCreateTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public TeamCreateTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("name", TeamToolSupport.textProperty("Team name"));
        properties.set("leadAgentId", TeamToolSupport.textProperty("Optional Lead agent id"));
        this.schema = TeamToolSupport.rootSchema(properties, "name");
    }

    @Override public String name() { return "TeamCreate"; }
    @Override public String description() { return "Create or select a long-lived team for this repository."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        TeamRecord team = manager.createTeam(TeamToolSupport.text(input, "name"), TeamToolSupport.text(input, "leadAgentId"));
        return ToolResult.success("team created: " + team.name() + "\npath: " + team.directory(), Map.of("team", team.name(), "path", team.directory().toString()));
    }

    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isDestructive() { return false; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
    @Override public String category() { return "team"; }
    @Override public ValidationError validateInput(JsonNode input) { return TeamToolSupport.require(input, "name", "missing_name"); }
}
