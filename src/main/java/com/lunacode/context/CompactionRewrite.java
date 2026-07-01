package com.lunacode.context;

import com.lunacode.conversation.ConversationMessageSnapshot;

import java.nio.file.Path;
import java.util.List;

public record CompactionRewrite(
        boolean success,
        List<ConversationMessageSnapshot> rewrittenMessages,
        int summarizedMessages,
        int retainedMessages,
        int restoredFiles,
        Path sessionLogPath,
        String failureReason
) {
    public CompactionRewrite {
        rewrittenMessages = rewrittenMessages == null ? List.of() : List.copyOf(rewrittenMessages);
    }

    public static CompactionRewrite failure(String reason) {
        return new CompactionRewrite(false, List.of(), 0, 0, 0, null, reason);
    }
}
