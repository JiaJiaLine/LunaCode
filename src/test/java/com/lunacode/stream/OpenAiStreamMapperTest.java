package com.lunacode.stream;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiStreamMapperTest {
    @Test
    void mapsOpenAiChunksToUnifiedEvents() {
        OpenAiStreamMapper mapper = new OpenAiStreamMapper();

        List<StreamEvent> first = mapper.map(new SseEvent("message", "{\"choices\":[{\"delta\":{\"content\":\"he\"},\"finish_reason\":null}]}"));
        assertEquals(3, first.size());
        assertInstanceOf(StreamEvent.MessageStart.class, first.get(0));
        assertInstanceOf(StreamEvent.ContentBlockStart.class, first.get(1));
        StreamEvent.ContentDelta firstDelta = (StreamEvent.ContentDelta) first.get(2);
        assertEquals("he", firstDelta.text());

        List<StreamEvent> second = mapper.map(new SseEvent("message", "{\"choices\":[{\"delta\":{\"content\":\"llo\"},\"finish_reason\":null}]}"));
        assertEquals(1, second.size());
        assertEquals("llo", ((StreamEvent.ContentDelta) second.get(0)).text());

        List<StreamEvent> usage = mapper.map(new SseEvent("message", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":3,\"total_tokens\":5}}"));
        assertEquals(2, usage.size());
        StreamEvent.MessageDelta usageDelta = (StreamEvent.MessageDelta) usage.get(0);
        assertEquals(2, usageDelta.usage().inputTokens());
        assertEquals(3, usageDelta.usage().outputTokens());
        assertEquals(5, usageDelta.usage().totalTokens());
        assertEquals("stop", ((StreamEvent.MessageDelta) usage.get(1)).stopReason());

        List<StreamEvent> done = mapper.map(new SseEvent("message", "[DONE]"));
        assertEquals(2, done.size());
        assertInstanceOf(StreamEvent.ContentBlockStop.class, done.get(0));
        assertInstanceOf(StreamEvent.MessageStop.class, done.get(1));
        assertEquals(5, ((StreamEvent.MessageStop) done.get(1)).usage().totalTokens());
    }

    @Test
    void mapsToolCallDeltasToToolUse() {
        OpenAiStreamMapper mapper = new OpenAiStreamMapper();

        List<StreamEvent> first = mapper.map(new SseEvent("message", "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"WriteFile\",\"arguments\":\"{\\\"path\\\":\"}}]},\"finish_reason\":null}]}"));
        assertEquals(1, first.size());
        assertInstanceOf(StreamEvent.MessageStart.class, first.get(0));

        List<StreamEvent> second = mapper.map(new SseEvent("message", "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"a.txt\\\",\\\"content\\\":\\\"ok\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}"));

        assertEquals(2, second.size());
        StreamEvent.ToolUse toolUse = (StreamEvent.ToolUse) second.get(0);
        assertEquals("call_1", toolUse.id());
        assertEquals("WriteFile", toolUse.name());
        assertEquals("a.txt", toolUse.input().path("path").asText());
        assertEquals("ok", toolUse.input().path("content").asText());
        assertEquals("tool_calls", ((StreamEvent.MessageDelta) second.get(1)).stopReason());
    }

    @Test
    void malformedJsonProducesError() {
        OpenAiStreamMapper mapper = new OpenAiStreamMapper();

        StreamEvent event = mapper.map(new SseEvent("message", "{")).get(0);

        assertInstanceOf(StreamEvent.Error.class, event);
    }
}