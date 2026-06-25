package com.lunacode.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.TokenUsage;

import java.util.ArrayList;
import java.util.List;

public class OpenAiStreamMapper {
    private final ObjectMapper mapper = new ObjectMapper();
    private boolean messageStarted;
    private boolean contentBlockStarted;
    private boolean contentBlockStopped;
    private TokenUsage finalUsage = TokenUsage.unknown();

    public List<StreamEvent> map(SseEvent event) {
        if ("[DONE]".equals(event.data())) {
            List<StreamEvent> events = new ArrayList<>();
            if (contentBlockStarted && !contentBlockStopped) {
                events.add(new StreamEvent.ContentBlockStop(0));
                contentBlockStopped = true;
            }
            events.add(new StreamEvent.MessageStop(finalUsage));
            return List.copyOf(events);
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
                String content = textOrNull(choice.path("delta"), "content");
                if (content != null && !content.isEmpty()) {
                    if (!contentBlockStarted) {
                        events.add(new StreamEvent.ContentBlockStart(0, "text"));
                        contentBlockStarted = true;
                    }
                    events.add(new StreamEvent.ContentDelta(0, content));
                }
                String finishReason = textOrNull(choice, "finish_reason");
                if (finishReason != null) {
                    events.add(new StreamEvent.MessageDelta(TokenUsage.unknown(), finishReason));
                }
            }
            return List.copyOf(events);
        } catch (Exception e) {
            return List.of(new StreamEvent.Error("OpenAI 流事件解析失败", e));
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
}