package com.lunacode.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.CacheUsageStatus;
import com.lunacode.conversation.TokenUsage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnthropicStreamMapper {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Integer, ToolUseBuffer> toolBuffers = new HashMap<>();

    public List<StreamEvent> map(SseEvent event) {
        try {
            return switch (event.event()) {
                case "message_start" -> List.of(mapMessageStart(parse(event.data())));
                case "content_block_start" -> List.of(mapContentBlockStart(parse(event.data())));
                case "content_block_delta" -> mapContentBlockDelta(parse(event.data()));
                case "content_block_stop" -> mapContentBlockStop(parse(event.data()));
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
        return new StreamEvent.MessageStart(usageFrom(usage, true));
    }

    private StreamEvent.ContentBlockStart mapContentBlockStart(JsonNode root) {
        int index = root.path("index").asInt(0);
        JsonNode block = root.path("content_block");
        String type = block.path("type").asText("unknown");
        if ("tool_use".equals(type)) {
            toolBuffers.put(index, new ToolUseBuffer(block.path("id").asText(), block.path("name").asText()));
        }
        return new StreamEvent.ContentBlockStart(index, type);
    }

    private List<StreamEvent> mapContentBlockDelta(JsonNode root) {
        int index = root.path("index").asInt(0);
        JsonNode delta = root.path("delta");
        String type = delta.path("type").asText();
        if ("text_delta".equals(type)) {
            return List.of(new StreamEvent.ContentDelta(index, delta.path("text").asText("")));
        }
        if ("input_json_delta".equals(type)) {
            ToolUseBuffer buffer = toolBuffers.get(index);
            if (buffer != null) {
                buffer.append(delta.path("partial_json").asText(""));
            }
        }
        return List.of();
    }

    private List<StreamEvent> mapContentBlockStop(JsonNode root) throws Exception {
        int index = root.path("index").asInt(0);
        ToolUseBuffer buffer = toolBuffers.remove(index);
        if (buffer == null) {
            return List.of(new StreamEvent.ContentBlockStop(index));
        }
        JsonNode input = buffer.json().isBlank() ? mapper.createObjectNode() : mapper.readTree(buffer.json());
        return List.of(new StreamEvent.ToolUse(buffer.id(), buffer.name(), input), new StreamEvent.ContentBlockStop(index));
    }

    private StreamEvent.MessageDelta mapMessageDelta(JsonNode root) {
        JsonNode usage = root.path("usage");
        String stopReason = textOrNull(root.path("delta"), "stop_reason");
        return new StreamEvent.MessageDelta(usageFrom(usage, false), stopReason);
    }

    private TokenUsage usageFrom(JsonNode usage, boolean startEvent) {
        if (usage.isMissingNode() || usage.isNull()) {
            return TokenUsage.unknown();
        }
        Integer cacheRead = intOrNull(usage, "cache_read_input_tokens");
        Integer cacheCreation = intOrNull(usage, "cache_creation_input_tokens");
        CacheUsageStatus status = cacheRead != null || cacheCreation != null ? CacheUsageStatus.SUPPORTED : CacheUsageStatus.UNKNOWN;
        return new TokenUsage(
                startEvent ? intOrNull(usage, "input_tokens") : null,
                startEvent ? null : intOrNull(usage, "output_tokens"),
                null,
                cacheRead,
                cacheCreation,
                status
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
