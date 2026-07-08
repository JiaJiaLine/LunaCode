package com.lunacode.team.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.subagent.AgentExecutionContextHolder;
import com.lunacode.subagent.SubAgentParentContext;
import com.lunacode.team.TeamManager;
import com.lunacode.team.TeamRuntimeContext;
import com.lunacode.team.TeamRuntimeRole;
import com.lunacode.tool.ValidationError;

import java.util.Optional;

final class TeamToolSupport {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private TeamToolSupport() {
    }

    static ObjectNode rootSchema(ObjectNode properties, String... required) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "object");
        root.set("properties", properties);
        var requiredNode = MAPPER.createArrayNode();
        for (String name : required) {
            requiredNode.add(name);
        }
        root.set("required", requiredNode);
        return root;
    }

    static ObjectNode textProperty(String description) {
        return MAPPER.createObjectNode().put("type", "string").put("description", description);
    }

    static String text(JsonNode input, String field) {
        return input == null || !input.hasNonNull(field) ? "" : input.path(field).asText("").strip();
    }

    static Optional<String> optionalText(JsonNode input, String field) {
        String value = text(input, field);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    static ValidationError require(JsonNode input, String field, String code) {
        return text(input, field).isBlank() ? new ValidationError(code, field + " is required") : null;
    }

    static String teamName(TeamManager manager, JsonNode input) {
        String explicit = text(input, "team");
        if (!explicit.isBlank()) {
            return explicit;
        }
        TeamRuntimeContext context = runtimeContext();
        if (context.active()) {
            return context.teamName();
        }
        return manager.currentTeam()
                .orElseThrow(() -> new IllegalStateException("no current team"))
                .name();
    }

    static String actor(JsonNode input) {
        String explicit = text(input, "actor");
        if (!explicit.isBlank()) {
            return explicit;
        }
        TeamRuntimeContext context = runtimeContext();
        if (context.role() == TeamRuntimeRole.MEMBER) {
            return context.memberName().orElse(context.agentId().orElse("member"));
        }
        return context.role() == TeamRuntimeRole.LEAD ? "lead" : "lead";
    }

    static TeamRuntimeContext runtimeContext() {
        SubAgentParentContext parent = AgentExecutionContextHolder.current();
        AgentRunConfig config = parent == null ? null : parent.parentConfig();
        return config == null ? TeamRuntimeContext.none() : config.teamRuntimeContext();
    }
}
