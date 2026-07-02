package com.lunacode.session;

import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;

import java.util.List;

public final class SessionTitleDeriver {
    private static final int MAX_TITLE_CHARS = 48;

    public String derive(List<ConversationMessageSnapshot> messages) {
        if (messages == null) {
            return "新会话";
        }
        for (ConversationMessageSnapshot message : messages) {
            if (message.role() == MessageRole.USER && !message.content().isBlank()) {
                String oneLine = message.content().replaceAll("\\s+", " ").strip();
                return oneLine.length() <= MAX_TITLE_CHARS
                        ? oneLine
                        : oneLine.substring(0, MAX_TITLE_CHARS) + "...";
            }
        }
        return "新会话";
    }
}
