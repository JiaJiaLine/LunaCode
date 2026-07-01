package com.lunacode.conversation;

import com.lunacode.context.ExternalizedToolResultRef;

import java.util.List;

public interface ConversationCompactionAccess {
    List<ConversationMessageSnapshot> fullSnapshot();

    void replaceToolResultContent(
            String messageId,
            String toolUseId,
            ContentBlock.ToolResultBlock replacement,
            ExternalizedToolResultRef ref
    );

    void rewriteForCompaction(List<ConversationMessageSnapshot> rewrittenMessages);
}
