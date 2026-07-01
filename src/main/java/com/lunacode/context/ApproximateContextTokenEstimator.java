package com.lunacode.context;

import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.prompt.PromptBundle;

import java.util.List;

public final class ApproximateContextTokenEstimator implements ContextTokenEstimator {
    private Long anchorInputTokens;
    private Long anchorChars;

    @Override
    public synchronized TokenEstimate estimate(PromptBundle bundle, ContextConfig config) {
        long chars = 0;
        if (bundle != null) {
            chars += bundle.system().staticPrompt().render().length();
            chars += bundle.toolDeclarations().toString().length();
            for (ApiMessage message : bundle.messages().history()) {
                chars += message.role().length();
                for (ContentBlock block : message.content()) {
                    chars += blockChars(block);
                }
            }
        }
        return new TokenEstimate(tokensFor(chars), chars, "prompt_bundle");
    }

    @Override
    public synchronized TokenEstimate estimateMessages(List<ConversationMessageSnapshot> messages, ContextConfig config) {
        long chars = ContextText.messageChars(messages);
        return new TokenEstimate(tokensFor(chars), chars, "conversation_messages");
    }

    @Override
    public synchronized void anchor(TokenUsage usage, TokenEstimate estimateAtRequest) {
        if (usage == null || usage.inputTokens() == null || estimateAtRequest == null) {
            return;
        }
        anchorInputTokens = usage.inputTokens().longValue();
        anchorChars = estimateAtRequest.estimatedChars();
    }

    private long tokensFor(long chars) {
        if (anchorInputTokens != null && anchorChars != null && chars >= anchorChars) {
            return anchorInputTokens + Math.max(0, (long) Math.ceil((chars - anchorChars) / 4.0));
        }
        return Math.max(1, (long) Math.ceil(chars / 4.0));
    }

    private long blockChars(ContentBlock block) {
        if (block instanceof ContentBlock.Text text) {
            return text.text().length();
        }
        if (block instanceof ContentBlock.ToolResultBlock toolResult) {
            return toolResult.content().length();
        }
        if (block instanceof ContentBlock.ToolUseBlock toolUse) {
            return toolUse.name().length() + (toolUse.input() == null ? 0 : toolUse.input().toString().length());
        }
        return 0;
    }
}

