package com.lunacode.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageMetadata;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryCompactorBoundaryTest {
    @Test
    void retentionBoundaryExpandsToKeepToolUseWithToolResult() {
        ObjectMapper mapper = new ObjectMapper();
        List<ConversationMessageSnapshot> messages = List.of(
                text("u1", MessageRole.USER, "早期请求"),
                text("a1", MessageRole.ASSISTANT, "早期回复"),
                new ConversationMessageSnapshot("a2", MessageRole.ASSISTANT, MessageStatus.COMPLETE, Instant.EPOCH, TokenUsage.unknown(), "", List.of(new ContentBlock.ToolUseBlock("toolu_1", "ReadFile", mapper.createObjectNode())), ConversationMessageMetadata.empty(), null),
                new ConversationMessageSnapshot("t1", MessageRole.TOOL, MessageStatus.COMPLETE, Instant.EPOCH, TokenUsage.unknown(), "结果", List.of(new ContentBlock.ToolResultBlock("toolu_1", "结果", false)), ConversationMessageMetadata.empty(), null),
                text("u2", MessageRole.USER, "继续")
        );
        ContextConfig config = new ContextConfig(20_000, 1_000, 1_000, 1_000, 50_000, 200_000, 1, 2, 5, 5_000, 25_000, 3, 3, 0.2, Path.of(".lunacode/tmp/context"));

        HistoryCompactor.RetentionSplit split = new HistoryCompactor().splitForRetention(messages, config);

        assertEquals("a2", split.retained().get(0).id());
        assertEquals(List.of("u1", "a1"), split.toSummarize().stream().map(ConversationMessageSnapshot::id).toList());
    }

    private ConversationMessageSnapshot text(String id, MessageRole role, String content) {
        return new ConversationMessageSnapshot(id, role, MessageStatus.COMPLETE, Instant.EPOCH, TokenUsage.unknown(), content, List.of(new ContentBlock.Text(content)), ConversationMessageMetadata.empty(), null);
    }
}
