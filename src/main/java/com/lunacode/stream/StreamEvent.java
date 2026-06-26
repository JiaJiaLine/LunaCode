package com.lunacode.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.lunacode.conversation.TokenUsage;

public sealed interface StreamEvent permits
        StreamEvent.MessageStart,
        StreamEvent.ContentBlockStart,
        StreamEvent.ContentDelta,
        StreamEvent.ContentBlockStop,
        StreamEvent.ToolUse,
        StreamEvent.MessageDelta,
        StreamEvent.MessageStop,
        StreamEvent.Error {

    record MessageStart(TokenUsage usage) implements StreamEvent {}

    record ContentBlockStart(int index, String type) implements StreamEvent {}

    record ContentDelta(int index, String text) implements StreamEvent {}

    record ContentBlockStop(int index) implements StreamEvent {}

    record ToolUse(String id, String name, JsonNode input) implements StreamEvent {}

    record MessageDelta(TokenUsage usage, String stopReason) implements StreamEvent {}

    record MessageStop(TokenUsage usage) implements StreamEvent {}

    record Error(String summary, Throwable cause) implements StreamEvent {}
}
