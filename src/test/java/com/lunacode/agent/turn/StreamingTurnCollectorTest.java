package com.lunacode.agent.turn;

import com.lunacode.agent.event.AgentEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamingTurnCollectorTest {
    @Test
    void collectsTextToolsUsageAndErrors() {
        ConversationManager manager = new DefaultConversationManager();
        String assistantId = manager.addStreamingAssistantMessage();
        List<AgentEvent> events = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        AgentTurnResult result = new StreamingTurnCollector(manager).collect(1, Stream.of(
                new StreamEvent.MessageStart(new TokenUsage(2, null, null)),
                new StreamEvent.ContentDelta(0, "hello"),
                new StreamEvent.ToolUse("toolu_1", "ReadFile", mapper.createObjectNode().put("path", "pom.xml")),
                new StreamEvent.MessageStop(new TokenUsage(2, 3, 5))
        ), assistantId, events::add, TokenUsage.unknown());

        assertEquals("hello", result.fullText());
        assertEquals(1, result.toolUses().size());
        assertEquals(AgentTurnState.COMPLETED, result.finalState());
        assertEquals("hello", manager.snapshot().get(0).content());
        assertEquals(MessageStatus.STREAMING, manager.snapshot().get(0).status());
        assertTrue(events.stream().anyMatch(AgentEvent.StreamText.class::isInstance));
        assertTrue(events.stream().anyMatch(AgentEvent.ToolUseStarted.class::isInstance));
        assertTrue(events.stream().anyMatch(AgentEvent.UsageUpdated.class::isInstance));
    }
}