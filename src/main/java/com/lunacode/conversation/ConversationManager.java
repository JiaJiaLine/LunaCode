package com.lunacode.conversation;

import java.util.List;

public interface ConversationManager {
    String addMessage(MessageRole role, String content);

    String addStreamingAssistantMessage();

    void appendContent(String messageId, String delta);

    void completeMessage(String messageId, TokenUsage usage);

    void failMessage(String messageId, String errorSummary);

    List<InternalMessage> snapshot();

    List<ApiMessage> toAPIFormat();
}