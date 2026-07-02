package com.lunacode.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.ProviderConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProviderMemoryModelClient implements MemoryModelClient {
    private final ChatProvider provider;
    private final ProviderConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProviderMemoryModelClient(ChatProvider provider, ProviderConfig config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public List<MemoryUpdateAction> proposeUpdates(MemoryUpdateRequest request) {
        if (provider == null || config == null || request == null || request.turnDelta().isEmpty()) {
            return List.of(new MemoryUpdateAction(MemoryUpdateAction.ActionKind.NOOP, MemoryType.PROJECT_KNOWLEDGE, null, null, null));
        }
        StringBuilder response = new StringBuilder();
        try (var events = provider.streamChat(List.of(new ApiMessage("user", prompt(request))), config)) {
            for (StreamEvent event : (Iterable<StreamEvent>) events::iterator) {
                if (event instanceof StreamEvent.ContentDelta delta) {
                    response.append(delta.text());
                } else if (event instanceof StreamEvent.Error error) {
                    throw new MemoryModelException(error.summary(), error.cause());
                }
            }
        }
        return parse(response.toString());
    }

    private String prompt(MemoryUpdateRequest request) {
        StringBuilder transcript = new StringBuilder();
        for (ConversationMessageSnapshot message : request.turnDelta()) {
            transcript.append(message.role()).append(": ").append(message.content()).append("\n");
        }
        return """
                你是 LunaCode 的自动记忆整理器。请只输出 JSON 数组，不要输出解释。
                可用 action: add, update, delete, noop。
                可用 type: user_preference, correction_feedback, project_knowledge, reference_info。
                每个对象字段: action, type, target_id, title, body。
                如果没有值得记住的内容，输出 [{"action":"noop","type":"project_knowledge"}]。

                [现有记忆索引]
                %s

                [本轮对话增量]
                %s
                """.formatted(request.currentIndex().mergedContent(), transcript).strip();
    }

    private List<MemoryUpdateAction> parse(String raw) {
        String json = raw == null ? "" : raw.strip();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end >= start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return List.of(new MemoryUpdateAction(MemoryUpdateAction.ActionKind.NOOP, MemoryType.PROJECT_KNOWLEDGE, null, null, null));
            }
            List<MemoryUpdateAction> actions = new ArrayList<>();
            for (JsonNode item : root) {
                actions.add(new MemoryUpdateAction(
                        actionKind(item.path("action").asText("noop")),
                        MemoryType.fromValue(item.path("type").asText("project_knowledge")),
                        optional(item.path("target_id").asText(null)),
                        optional(item.path("title").asText(null)),
                        optional(item.path("body").asText(null))
                ));
            }
            return actions.isEmpty()
                    ? List.of(new MemoryUpdateAction(MemoryUpdateAction.ActionKind.NOOP, MemoryType.PROJECT_KNOWLEDGE, null, null, null))
                    : List.copyOf(actions);
        } catch (Exception e) {
            throw new MemoryModelException("记忆模型输出无法解析", e);
        }
    }

    private MemoryUpdateAction.ActionKind actionKind(String value) {
        return switch ((value == null ? "" : value).toLowerCase(Locale.ROOT)) {
            case "add" -> MemoryUpdateAction.ActionKind.ADD;
            case "update" -> MemoryUpdateAction.ActionKind.UPDATE;
            case "delete" -> MemoryUpdateAction.ActionKind.DELETE;
            default -> MemoryUpdateAction.ActionKind.NOOP;
        };
    }

    private java.util.Optional<String> optional(String value) {
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }

    public static class MemoryModelException extends RuntimeException {
        public MemoryModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
