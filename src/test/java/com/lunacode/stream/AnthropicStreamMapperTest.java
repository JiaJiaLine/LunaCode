package com.lunacode.stream;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicStreamMapperTest {
    @Test
    void mapsCompleteAnthropicSequence() {
        AnthropicStreamMapper mapper = new AnthropicStreamMapper();

        StreamEvent.MessageStart start = (StreamEvent.MessageStart) only(mapper.map(new SseEvent(
                "message_start",
                "{\"message\":{\"usage\":{\"input_tokens\":7}}}"
        )));
        assertEquals(7, start.usage().inputTokens());

        StreamEvent.ContentBlockStart blockStart = (StreamEvent.ContentBlockStart) only(mapper.map(new SseEvent(
                "content_block_start",
                "{\"index\":0,\"content_block\":{\"type\":\"text\"}}"
        )));
        assertEquals(0, blockStart.index());
        assertEquals("text", blockStart.type());

        StreamEvent.ContentDelta delta = (StreamEvent.ContentDelta) only(mapper.map(new SseEvent(
                "content_block_delta",
                "{\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}"
        )));
        assertEquals("你好", delta.text());

        StreamEvent.ContentBlockStop blockStop = (StreamEvent.ContentBlockStop) only(mapper.map(new SseEvent(
                "content_block_stop",
                "{\"index\":0}"
        )));
        assertEquals(0, blockStop.index());

        StreamEvent.MessageDelta messageDelta = (StreamEvent.MessageDelta) only(mapper.map(new SseEvent(
                "message_delta",
                "{\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":9}}"
        )));
        assertEquals(9, messageDelta.usage().outputTokens());
        assertEquals("end_turn", messageDelta.stopReason());

        assertInstanceOf(StreamEvent.MessageStop.class, only(mapper.map(new SseEvent("message_stop", "{}"))));
    }

    @Test
    void malformedJsonProducesError() {
        AnthropicStreamMapper mapper = new AnthropicStreamMapper();

        StreamEvent event = only(mapper.map(new SseEvent("message_start", "{")));

        assertInstanceOf(StreamEvent.Error.class, event);
    }

    @Test
    void malformedToolInputJsonFallsBackToEmptyObject() {
        AnthropicStreamMapper mapper = new AnthropicStreamMapper();
        mapper.map(new SseEvent("content_block_start", "{\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\",\"name\":\"SendMessage\"}}"));
        mapper.map(new SseEvent("content_block_delta", "{\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{bad\"}}"));

        List<StreamEvent> events = mapper.map(new SseEvent("content_block_stop", "{\"index\":0}"));

        assertEquals(2, events.size());
        StreamEvent.ToolUse toolUse = (StreamEvent.ToolUse) events.get(0);
        assertEquals("SendMessage", toolUse.name());
        assertTrue(toolUse.input().isObject());
        assertEquals(0, toolUse.input().size());
    }

    private StreamEvent only(List<StreamEvent> events) {
        assertEquals(1, events.size());
        return events.get(0);
    }
}