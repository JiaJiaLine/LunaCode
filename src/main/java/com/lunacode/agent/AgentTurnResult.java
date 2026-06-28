package com.lunacode.agent;

import com.lunacode.conversation.TokenUsage;
import com.lunacode.tool.ToolUse;

import java.util.List;

public record AgentTurnResult(
        int turnIndex,
        String assistantMessageId,
        String fullText,
        List<ToolUse> toolUses,
        TokenUsage usage,
        AgentTurnState finalState,
        String errorSummary
) {}
