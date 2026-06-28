package com.lunacode.agent.turn;

import com.lunacode.agent.event.AgentEventSink;

import com.lunacode.agent.event.AgentEvent;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AgentTurnRunnerTest {
    @Test
    void passesSystemPromptAndEmitsTurnComplete() {
        ConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider();
        List<AgentEvent> events = new java.util.ArrayList<>();

        AgentTurnResult result = new AgentTurnRunner(manager, provider).runTurn(new AgentTurnInput(
                2,
                "system prompt",
                List.of(new ApiMessage("user", "hello")),
                new ProviderConfig("openai", "test", URI.create("https://api.example.com"), "key", ThinkingConfig.disabled()),
                new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode(),
                com.lunacode.conversation.TokenUsage.unknown(),
                events::add
        ));

        assertEquals("system prompt", provider.systemPrompt);
        assertEquals(AgentTurnState.COMPLETED, result.finalState());
        assertTrue(events.stream().anyMatch(event -> event instanceof AgentEvent.TurnComplete turn && turn.turnIndex() == 2));
    }

    private static class CapturingProvider implements ChatProvider {
        private String systemPrompt;

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            return Stream.of(new StreamEvent.ContentDelta(0, "ok"), new StreamEvent.MessageStop(com.lunacode.conversation.TokenUsage.unknown()));
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config, ArrayNode enabledTools, String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return streamChat(messages, config);
        }
    }
}