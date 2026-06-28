package com.lunacode.provider;

import com.lunacode.conversation.CacheUsageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.stream.AnthropicStreamMapper;
import com.lunacode.stream.OpenAiStreamMapper;
import com.lunacode.stream.SseEvent;
import com.lunacode.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderCacheUsageTest {
    @Test
    void tokenUsageMergesCacheFields() {
        TokenUsage base = new TokenUsage(1, 2, 3);
        TokenUsage newer = new TokenUsage(null, 4, null, 10, 20, CacheUsageStatus.SUPPORTED);

        TokenUsage merged = base.merge(newer);

        assertEquals(1, merged.inputTokens());
        assertEquals(4, merged.outputTokens());
        assertEquals(3, merged.totalTokens());
        assertEquals(10, merged.cacheReadInputTokens());
        assertEquals(20, merged.cacheCreationInputTokens());
        assertEquals(CacheUsageStatus.SUPPORTED, merged.cacheStatus());
    }

    @Test
    void anthropicMapperParsesCacheUsageFields() {
        var events = new AnthropicStreamMapper().map(new SseEvent("message_start", "{\"message\":{\"usage\":{\"input_tokens\":100,\"cache_read_input_tokens\":30,\"cache_creation_input_tokens\":40}}}"));

        StreamEvent.MessageStart start = (StreamEvent.MessageStart) events.get(0);

        assertEquals(100, start.usage().inputTokens());
        assertEquals(30, start.usage().cacheReadInputTokens());
        assertEquals(40, start.usage().cacheCreationInputTokens());
        assertEquals(CacheUsageStatus.SUPPORTED, start.usage().cacheStatus());
    }

    @Test
    void openAiMapperParsesCachedPromptTokensWhenPresent() {
        var events = new OpenAiStreamMapper().map(new SseEvent("message", "{\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":5,\"total_tokens\":105,\"prompt_tokens_details\":{\"cached_tokens\":80}},\"choices\":[]}"));

        StreamEvent.MessageDelta delta = (StreamEvent.MessageDelta) events.stream()
                .filter(StreamEvent.MessageDelta.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertEquals(80, delta.usage().cacheReadInputTokens());
        assertEquals(CacheUsageStatus.SUPPORTED, delta.usage().cacheStatus());
    }
}
