package com.lunacode.context;

import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageSnapshot;

import java.util.List;

final class ContextText {
    private ContextText() {
    }

    static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil(text.length() / 4.0));
    }

    static long messageChars(List<ConversationMessageSnapshot> messages) {
        long chars = 0;
        if (messages == null) {
            return 0;
        }
        for (ConversationMessageSnapshot message : messages) {
            chars += messageChars(message);
        }
        return chars;
    }

    static long messageChars(ConversationMessageSnapshot message) {
        if (message == null) {
            return 0;
        }
        long chars = safeLength(message.content());
        for (ContentBlock block : message.blocks()) {
            if (block instanceof ContentBlock.Text text) {
                chars += safeLength(text.text());
            } else if (block instanceof ContentBlock.ToolResultBlock toolResult) {
                chars += safeLength(toolResult.content());
            } else if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                chars += safeLength(toolUse.name()) + safeLength(toolUse.input() == null ? "" : toolUse.input().toString());
            }
        }
        return chars;
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
