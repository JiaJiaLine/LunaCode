package com.lunacode.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.TokenUsage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiStreamMapper {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Integer, ToolCallBuffer> toolCalls = new LinkedHashMap<>();
    private boolean messageStarted;
    private boolean contentBlockStarted;
    private boolean contentBlockStopped;
    private TokenUsage finalUsage = TokenUsage.unknown();

    public List<StreamEvent> map(SseEvent event) {
        if ("[DONE]".equals(event.data())) {
            try {
                List<StreamEvent> events = new ArrayList<>();
                if (!toolCalls.isEmpty()) {
                    events.addAll(flushToolCalls());
                }
                if (contentBlockStarted && !contentBlockStopped) {
                    events.add(new StreamEvent.ContentBlockStop(0));
                    contentBlockStopped = true;
                }
                events.add(new StreamEvent.MessageStop(finalUsage));
                return List.copyOf(events);
            } catch (Exception e) {
                return List.of(new StreamEvent.Error("OpenAI stream event parse failed", e));
            }
        }

        try {
            JsonNode root = mapper.readTree(event.data());
            List<StreamEvent> events = new ArrayList<>();
            if (!messageStarted) {
                events.add(new StreamEvent.MessageStart(TokenUsage.unknown()));
                messageStarted = true;
            }

            TokenUsage usage = usageFrom(root.path("usage"));
            if (usage.inputTokens() != null || usage.outputTokens() != null || usage.totalTokens() != null) {
                finalUsage = finalUsage.merge(usage);
                events.add(new StreamEvent.MessageDelta(finalUsage, null));
            }

            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                JsonNode delta = choice.path("delta");
                String content = textOrNull(delta, "content");
                if (content != null && !content.isEmpty()) {
                    if (!contentBlockStarted) {
                        events.add(new StreamEvent.ContentBlockStart(0, "text"));
                        contentBlockStarted = true;
                    }
                    events.add(new StreamEvent.ContentDelta(0, content));
                }

                collectToolCalls(delta.path("tool_calls"));
                String finishReason = textOrNull(choice, "finish_reason");
                if ("tool_calls".equals(finishReason)) {
                    events.addAll(flushToolCalls());
                }
                if (finishReason != null) {
                    events.add(new StreamEvent.MessageDelta(TokenUsage.unknown(), finishReason));
                }
            }
            return List.copyOf(events);
        } catch (Exception e) {
            return List.of(new StreamEvent.Error("OpenAI stream event parse failed", e));
        }
    }

    private void collectToolCalls(JsonNode calls) {
        if (!calls.isArray()) {
            return;
        }
        for (JsonNode call : calls) {
            int index = call.path("index").asInt(0);
            ToolCallBuffer buffer = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuffer());
            appendIfPresent(buffer.id, call, "id");
            JsonNode function = call.path("function");
            appendIfPresent(buffer.name, function, "name");
            appendIfPresent(buffer.arguments, function, "arguments");
        }
    }

    private List<StreamEvent> flushToolCalls() throws Exception {
        List<StreamEvent> events = new ArrayList<>();
        for (Map.Entry<Integer, ToolCallBuffer> entry : toolCalls.entrySet()) {
            ToolCallBuffer buffer = entry.getValue();
            String id = buffer.id.isEmpty() ? "openai_tool_" + entry.getKey() : buffer.id.toString();
            String name = buffer.name.toString();
            String arguments = buffer.arguments.isEmpty() ? "{}" : buffer.arguments.toString();
            JsonNode input = mapper.readTree(arguments);
            events.add(new StreamEvent.ToolUse(id, name, input));
        }
        toolCalls.clear();
        return events;
    }

    private void appendIfPresent(StringBuilder target, JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isMissingNode() && !value.isNull()) {
            target.append(value.asText());
        }
    }

    private TokenUsage usageFrom(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return TokenUsage.unknown();
        }
        return new TokenUsage(
                intOrNull(usage, "prompt_tokens"),
                intOrNull(usage, "completion_tokens"),
                intOrNull(usage, "total_tokens")
        );
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static final class ToolCallBuffer {
        private final StringBuilder id = new StringBuilder();
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }
}