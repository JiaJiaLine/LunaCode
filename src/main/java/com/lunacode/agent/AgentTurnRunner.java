package com.lunacode.agent;

import com.lunacode.conversation.ConversationManager;
import com.lunacode.provider.ChatProvider;
import com.lunacode.stream.StreamEvent;

import java.util.Objects;
import java.util.stream.Stream;

public final class AgentTurnRunner {
    private final ConversationManager conversationManager;
    private final ChatProvider provider;
    private final StreamingTurnCollector collector;

    public AgentTurnRunner(ConversationManager conversationManager, ChatProvider provider) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.collector = new StreamingTurnCollector(conversationManager);
    }

    public AgentTurnResult runTurn(AgentTurnInput input) {
        String assistantId = conversationManager.addStreamingAssistantMessage();
        try {
            Stream<StreamEvent> events = provider.streamChat(
                    input.messages(),
                    input.providerConfig(),
                    input.enabledTools(),
                    input.systemPrompt()
            );
            AgentTurnResult result = collector.collect(input.turnIndex(), events, assistantId, input.sink(), input.cumulativeUsage());
            if (result.finalState() == AgentTurnState.COMPLETED) {
                conversationManager.completeMessage(assistantId, result.usage());
            } else if (result.finalState() == AgentTurnState.FAILED) {
                conversationManager.failMessage(assistantId, result.errorSummary());
            }
            if (input.sink() != null) {
                input.sink().emit(new AgentEvent.TurnComplete(input.turnIndex()));
            }
            return result;
        } catch (Exception e) {
            String summary = "模型响应失败: " + e.getMessage();
            conversationManager.failMessage(assistantId, summary);
            if (input.sink() != null) {
                input.sink().emit(new AgentEvent.ErrorOccurred(summary, e));
                input.sink().emit(new AgentEvent.TurnComplete(input.turnIndex()));
            }
            return new AgentTurnResult(input.turnIndex(), assistantId, "", java.util.List.of(), input.cumulativeUsage(), AgentTurnState.FAILED, summary);
        }
    }
}
