package com.lunacode.agent;

import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.ToolUse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class StreamingTurnCollector {
    private final ConversationManager conversationManager;

    public StreamingTurnCollector(ConversationManager conversationManager) {
        this.conversationManager = Objects.requireNonNull(conversationManager, "conversationManager");
    }

    public AgentTurnResult collect(
            int turnIndex,
            Stream<StreamEvent> providerEvents,
            String assistantMessageId,
            AgentEventSink sink,
            TokenUsage initialUsage
    ) {
        TokenUsage latestUsage = initialUsage == null ? TokenUsage.unknown() : initialUsage;
        List<ToolUse> toolUses = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        try (Stream<StreamEvent> events = providerEvents) {
            for (StreamEvent event : (Iterable<StreamEvent>) events::iterator) {
                if (event instanceof StreamEvent.MessageStart messageStart) {
                    latestUsage = latestUsage.merge(messageStart.usage());
                    emit(sink, new AgentEvent.UsageUpdated(latestUsage));
                } else if (event instanceof StreamEvent.ContentDelta contentDelta) {
                    fullText.append(contentDelta.text());
                    conversationManager.appendContent(assistantMessageId, contentDelta.text());
                    emit(sink, new AgentEvent.StreamText(contentDelta.text()));
                } else if (event instanceof StreamEvent.ToolUse toolUseEvent) {
                    ToolUse toolUse = new ToolUse(toolUseEvent.id(), toolUseEvent.name(), toolUseEvent.input());
                    toolUses.add(toolUse);
                    conversationManager.appendToolUse(assistantMessageId, new ContentBlock.ToolUseBlock(toolUse.id(), toolUse.name(), toolUse.input()));
                    emit(sink, new AgentEvent.ToolUseStarted(toolUse.id(), toolUse.name(), toolUse.input()));
                } else if (event instanceof StreamEvent.MessageDelta messageDelta) {
                    latestUsage = latestUsage.merge(messageDelta.usage());
                    emit(sink, new AgentEvent.UsageUpdated(latestUsage));
                } else if (event instanceof StreamEvent.MessageStop messageStop) {
                    latestUsage = latestUsage.merge(messageStop.usage());
                    emit(sink, new AgentEvent.UsageUpdated(latestUsage));
                } else if (event instanceof StreamEvent.Error error) {
                    emit(sink, new AgentEvent.ErrorOccurred(error.summary(), error.cause()));
                    return new AgentTurnResult(turnIndex, assistantMessageId, fullText.toString(), List.copyOf(toolUses), latestUsage, AgentTurnState.FAILED, error.summary());
                }
            }
            return new AgentTurnResult(turnIndex, assistantMessageId, fullText.toString(), List.copyOf(toolUses), latestUsage, AgentTurnState.COMPLETED, null);
        } catch (Exception e) {
            String summary = "模型流式响应失败: " + e.getMessage();
            emit(sink, new AgentEvent.ErrorOccurred(summary, e));
            return new AgentTurnResult(turnIndex, assistantMessageId, fullText.toString(), List.copyOf(toolUses), latestUsage, AgentTurnState.FAILED, summary);
        }
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        if (sink != null) {
            sink.emit(event);
        }
    }
}
