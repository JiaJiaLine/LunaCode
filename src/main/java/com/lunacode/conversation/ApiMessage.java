package com.lunacode.conversation;

import java.util.List;

public record ApiMessage(String role, List<ContentBlock> content) {
    public ApiMessage {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public ApiMessage(String role, String content) {
        this(role, List.of(new ContentBlock.Text(content)));
    }

    public String textContent() {
        StringBuilder result = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof ContentBlock.Text text) {
                result.append(text.text());
            }
        }
        return result.toString();
    }
}
