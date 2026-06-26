package com.lunacode.conversation;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface ContentBlock permits
        ContentBlock.Text,
        ContentBlock.ToolUseBlock,
        ContentBlock.ToolResultBlock {

    record Text(String text) implements ContentBlock {
        public Text {
            text = text == null ? "" : text;
        }
    }

    record ToolUseBlock(
            String id,
            String name,
            JsonNode input
    ) implements ContentBlock {}

    record ToolResultBlock(
            String toolUseId,
            String content,
            boolean isError
    ) implements ContentBlock {
        public ToolResultBlock {
            content = content == null ? "" : content;
        }
    }
}
