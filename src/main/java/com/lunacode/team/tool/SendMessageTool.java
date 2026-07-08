package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.team.TeamManager;
import com.lunacode.team.mailbox.TeamMessageRecord;
import com.lunacode.team.mailbox.TeamMessageType;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;

import java.util.Map;
import java.util.stream.Collectors;

public final class SendMessageTool implements Tool {
    private final TeamManager manager;
    private final JsonNode schema;

    public SendMessageTool(TeamManager manager) {
        this.manager = manager;
        ObjectNode properties = TeamToolSupport.MAPPER.createObjectNode();
        properties.set("team", TeamToolSupport.textProperty("Optional team name"));
        properties.set("to", TeamToolSupport.textProperty("Teammate name, agent id, or *"));
        properties.set("summary", TeamToolSupport.textProperty("5-10 word preview summary for text messages"));
        properties.set("message", TeamToolSupport.textProperty("Message body"));
        properties.set("type", TeamToolSupport.textProperty("TEXT, SHUTDOWN_REQUEST, SHUTDOWN_RESPONSE, PLAN_APPROVAL_RESPONSE"));
        properties.set("actor", TeamToolSupport.textProperty("Optional sender"));
        properties.set("decision", TeamToolSupport.textProperty("Optional approve/reject for structured messages"));
        properties.set("feedback", TeamToolSupport.textProperty("Optional approval feedback"));
        this.schema = TeamToolSupport.rootSchema(properties, "to", "message");
    }

    @Override public String name() { return "SendMessage"; }
    @Override public String description() { return "Send a mailbox message to a teammate by name, agent id, or * broadcast."; }
    @Override public JsonNode inputSchema() { return schema; }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        String teamName = TeamToolSupport.teamName(manager, input);
        TeamMessageType type = type(TeamToolSupport.text(input, "type"));
        String actor = TeamToolSupport.actor(input);
        Map<String, String> metadata = Map.of(
                "decision", TeamToolSupport.text(input, "decision"),
                "feedback", TeamToolSupport.text(input, "feedback")
        );
        var sent = manager.sendMessage(teamName, actor, TeamToolSupport.text(input, "to"), type, TeamToolSupport.text(input, "summary"), TeamToolSupport.text(input, "message"), metadata);
        String content = sent.stream().map(TeamMessageRecord::to).collect(Collectors.joining(", "));
        return ToolResult.success("message sent to: " + content, Map.of("team", teamName, "count", sent.size()));
    }

    private TeamMessageType type(String value) {
        if (value.isBlank()) {
            return TeamMessageType.TEXT;
        }
        return TeamMessageType.valueOf(value.toUpperCase());
    }

    @Override public boolean isReadOnly() { return false; }
    @Override public boolean isDestructive() { return false; }
    @Override public boolean isConcurrencySafe(JsonNode input) { return false; }
    @Override public String category() { return "team"; }

    @Override
    public ValidationError validateInput(JsonNode input) {
        ValidationError to = TeamToolSupport.require(input, "to", "missing_to");
        if (to != null) {
            return to;
        }
        return TeamToolSupport.require(input, "message", "missing_message");
    }
}
