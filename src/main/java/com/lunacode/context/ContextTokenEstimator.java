package com.lunacode.context;

import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.prompt.PromptBundle;

import java.util.List;

public interface ContextTokenEstimator {
    TokenEstimate estimate(PromptBundle bundle, ContextConfig config);

    TokenEstimate estimateMessages(List<ConversationMessageSnapshot> messages, ContextConfig config);

    void anchor(TokenUsage usage, TokenEstimate estimateAtRequest);
}
