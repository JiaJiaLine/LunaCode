package com.lunacode.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolMessageFormatTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsTextAndToolUseInSameAssistantMessageAndResultsInUserMessage() {
        ConversationManager manager = new DefaultConversationManager();
        manager.addMessage(MessageRole.USER, "读文件");
        String assistant = manager.addStreamingAssistantMessage();
        manager.appendContent(assistant, "我来读。");
        manager.appendToolUse(assistant, new ContentBlock.ToolUseBlock("toolu_1", "ReadFile", mapper.createObjectNode().put("path", "pom.xml")));
        manager.appendToolUse(assistant, new ContentBlock.ToolUseBlock("toolu_2", "Grep", mapper.createObjectNode().put("pattern", "ChatProvider")));
        manager.completeMessage(assistant, TokenUsage.unknown());
        manager.addUserToolResultMessage(List.of(
                new ContentBlock.ToolResultBlock("toolu_1", "pom content", false),
                new ContentBlock.ToolResultBlock("toolu_2", "grep content", false)
        ));

        List<ApiMessage> messages = manager.toAPIFormat();

        assertEquals(3, messages.size());
        assertEquals("assistant", messages.get(1).role());
        assertEquals(3, messages.get(1).content().size());
        assertInstanceOf(ContentBlock.Text.class, messages.get(1).content().get(0));
        assertInstanceOf(ContentBlock.ToolUseBlock.class, messages.get(1).content().get(1));
        assertInstanceOf(ContentBlock.ToolUseBlock.class, messages.get(1).content().get(2));
        assertEquals("user", messages.get(2).role());
        assertEquals(2, messages.get(2).content().size());
        assertInstanceOf(ContentBlock.ToolResultBlock.class, messages.get(2).content().get(0));
        ContentBlock.ToolResultBlock result = (ContentBlock.ToolResultBlock) messages.get(2).content().get(0);
        assertEquals("toolu_1", result.toolUseId());
    }
}