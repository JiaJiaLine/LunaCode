package com.lunacode.stream;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeToolUseStreamMapperTest {
    @Test
    void joinsInputJsonDeltasIntoToolUse() {
        AnthropicStreamMapper mapper = new AnthropicStreamMapper();

        assertInstanceOf(StreamEvent.ContentBlockStart.class, mapper.map(new SseEvent(
                "content_block_start",
                "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"ReadFile\",\"input\":{}}}"
        )).get(0));
        assertTrue(mapper.map(new SseEvent("content_block_delta", "{\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\"}}" )).isEmpty());
        assertTrue(mapper.map(new SseEvent("content_block_delta", "{\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"path\\\"\"}}" )).isEmpty());
        assertTrue(mapper.map(new SseEvent("content_block_delta", "{\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\": \\\"pom.xml\\\"}\"}}" )).isEmpty());

        List<StreamEvent> events = mapper.map(new SseEvent("content_block_stop", "{\"index\":1}"));

        assertEquals(2, events.size());
        StreamEvent.ToolUse toolUse = (StreamEvent.ToolUse) events.get(0);
        assertEquals("toolu_1", toolUse.id());
        assertEquals("ReadFile", toolUse.name());
        assertEquals("pom.xml", toolUse.input().path("path").asText());
        assertInstanceOf(StreamEvent.ContentBlockStop.class, events.get(1));
    }

    @Test
    void invalidJsonProducesError() {
        AnthropicStreamMapper mapper = new AnthropicStreamMapper();
        mapper.map(new SseEvent("content_block_start", "{\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"ReadFile\"}}"));
        mapper.map(new SseEvent("content_block_delta", "{\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\"}}"));

        StreamEvent event = mapper.map(new SseEvent("content_block_stop", "{\"index\":0}")).get(0);

        assertInstanceOf(StreamEvent.Error.class, event);
    }
}