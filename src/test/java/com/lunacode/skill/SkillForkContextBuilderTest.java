package com.lunacode.skill;

import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillForkContextBuilderTest {
    private final SkillForkContextBuilder builder = new SkillForkContextBuilder();

    @Test
    void noneDoesNotBringParentHistory() {
        DefaultConversationManager parent = conversation(3);

        assertTrue(builder.build(SkillContextPolicy.NONE, parent).isEmpty());
    }

    @Test
    void recentBringsOnlyLastFiveMessages() {
        DefaultConversationManager parent = conversation(8);

        var context = builder.build(SkillContextPolicy.RECENT, parent);

        assertEquals(5, context.size());
        assertEquals("message-4", context.get(0).content());
        assertEquals("message-8", context.get(4).content());
    }

    @Test
    void fullUsesSingleSummaryMessage() {
        DefaultConversationManager parent = conversation(3);

        var context = builder.build(SkillContextPolicy.FULL, parent);

        assertEquals(1, context.size());
        assertEquals(MessageRole.USER, context.get(0).role());
        assertTrue(context.get(0).content().contains("message-1"));
        assertTrue(context.get(0).content().contains("message-3"));
    }

    private DefaultConversationManager conversation(int count) {
        DefaultConversationManager manager = new DefaultConversationManager();
        for (int i = 1; i <= count; i++) {
            if (i % 2 == 0) {
                manager.addAssistantMessage(List.of(new ContentBlock.Text("message-" + i)));
            } else {
                manager.addMessage(MessageRole.USER, "message-" + i);
            }
        }
        return manager;
    }
}
