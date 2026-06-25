package com.lunacode.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.TokenUsage;

import java.util.List;

public class AnthropicStreamMapper {
    private final ObjectMapper mapper = new ObjectMapper();

    public List<StreamEvent> map(SseEvent event) {
        try {
            return switch (event.event()) {
                case "message_start" -> List.of(mapMessageStart(parse(event.data())));
                case "content_block_start" -> List.of(mapContentBlockStart(parse(event.data())));
                case "content_block_delta" -> mapContentBlockDelta(parse(event.data()));
                case "content_block_stop" -> List.of(mapContentBlockStop(parse(event.data())));
                case "message_delta" -> List.of(mapMessageDelta(parse(event.data())));
                case "message_stop" -> List.of(new StreamEvent.MessageStop(TokenUsage.unknown()));
                default -> List.of();
            };
        } catch (Exception e) {
            return List.of(new StreamEvent.Error("Anthropic 流事件解析失败", e));
        }
    }

    private JsonNode parse(String data) throws Exception {
        if (data == null || data.isBlank()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(data);
    }

    private StreamEvent.MessageStart mapMessageStart(JsonNode root) {
        JsonNode usage = root.path("message").path("usage");
        return new StreamEvent.MessageStart(new TokenUsage(intOrNull(usage, "input_tokens"), null, null));
    }

    private StreamEvent.ContentBlockStart mapContentBlockStart(JsonNode root) {
        int index = root.path("index").asInt(0);
        String type = root.path("content_block").path("type").asText("unknown");
        return new StreamEvent.ContentBlockStart(index, type);
    }

    private List<StreamEvent> mapContentBlockDelta(JsonNode root) {
        int index = root.path("index").asInt(0);
        JsonNode delta = root.path("delta");
        if (!"text_delta".equals(delta.path("type").asText())) {
            return List.of();
        }
        return List.of(new StreamEvent.ContentDelta(index, delta.path("text").asText("")));
    }

    private StreamEvent.ContentBlockStop mapContentBlockStop(JsonNode root) {
        return new StreamEvent.ContentBlockStop(root.path("index").asInt(0));
    }

    private StreamEvent.MessageDelta mapMessageDelta(JsonNode root) {
        JsonNode usage = root.path("usage");
        String stopReason = textOrNull(root.path("delta"), "stop_reason");
        return new StreamEvent.MessageDelta(new TokenUsage(null, intOrNull(usage, "output_tokens"), null), stopReason);
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