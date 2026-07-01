package com.lunacode.context;

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

class SummaryPromptBuilderTest {
    @Test
    void promptContainsRequiredSectionsNoToolRuleAndOriginalUserMessages() {
        ConversationMessageSnapshot user = message("1", MessageRole.USER, "用户原话 token=abc");

        String prompt = new SummaryPromptBuilder().build(List.of(user), Path.of(".lunacode/tmp/context/s/session.jsonl"));

        for (String section : List.of("主要请求和意图", "关键技术概念", "文件和代码段", "错误和修复", "问题解决过程", "所有用户消息", "待办任务", "当前工作", "可能的下一步")) {
            assertTrue(prompt.contains(section), section);
        }
        assertTrue(prompt.contains("严禁调用任何工具"));
        assertTrue(prompt.contains("用户原话 token=abc"));
    }

    @Test
    void parserDropsAnalysisDraft() {
        String parsed = new SummaryResponseParser().parseFinalSummary("""
                <analysis_draft>
                不应写回
                </analysis_draft>
                <final_summary>
                ## 主要请求和意图
                正式摘要
                </final_summary>
                """);

        assertEquals("## 主要请求和意图\n正式摘要", parsed);
    }

    private ConversationMessageSnapshot message(String id, MessageRole role, String content) {
        return new ConversationMessageSnapshot(
                id,
                role,
                MessageStatus.COMPLETE,
                Instant.EPOCH,
                TokenUsage.unknown(),
                content,
                List.of(new ContentBlock.Text(content)),
                ConversationMessageMetadata.empty(),
                null
        );
    }
}
