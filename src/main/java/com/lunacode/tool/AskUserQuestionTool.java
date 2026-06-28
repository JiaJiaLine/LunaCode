package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.agent.UserQuestionRequest;

import java.util.Map;

public final class AskUserQuestionTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonNode schema;

    public AskUserQuestionTool() {
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("question", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("question"));
    }

    @Override
    public String name() {
        return "AskUserQuestion";
    }

    @Override
    public String description() {
        return "Plan Mode 专用：一次提出一个聚焦的需求澄清问题，并等待用户回答。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        ValidationError validation = validateInput(input);
        if (validation != null) {
            return ToolResult.error(validation.message(), Map.of("errorType", "invalid_arguments", "code", validation.code()));
        }
        if (context.userQuestionBroker() == null) {
            return ToolResult.error("AskUserQuestion 没有可用的用户问题通道", Map.of("errorType", "user_question_broker_missing"));
        }
        try {
            String question = input.path("question").asText().strip();
            String answer = context.userQuestionBroker().ask(new UserQuestionRequest("AskUserQuestion", question));
            return ToolResult.success("用户回答：" + answer, Map.of("question", question));
        } catch (Exception e) {
            return ToolResult.error("需求澄清被取消或失败: " + e.getMessage(), Map.of("errorType", "user_question_failed"));
        }
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
        return "interaction";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("question") || input.path("question").asText().isBlank()) {
            return new ValidationError("missing_question", "AskUserQuestion 需要 question 参数");
        }
        return null;
    }
}
