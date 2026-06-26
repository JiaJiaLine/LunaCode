package com.lunacode.conversation;

import java.util.List;

public interface ConversationManager {
    String addMessage(MessageRole role, String content);

    String addAssistantMessage(List<ContentBlock> blocks);

    String addUserToolResultMessage(List<ContentBlock.ToolResultBlock> results);

    String addStreamingAssistantMessage();

    void appendContent(String messageId, String delta);

    void appendToolUse(String messageId, ContentBlock.ToolUseBlock toolUse);

    void completeMessage(String messageId, TokenUsage usage);

    void failMessage(String messageId, String errorSummary);

    List<InternalMessage> snapshot();

    List<ApiMessage> toAPIFormat();
}
