package com.lunacode.prompt;

import java.util.Objects;

public record PromptSection(PromptSectionKind kind, String title, String content) {
    public PromptSection {
        kind = Objects.requireNonNull(kind, "kind");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("提示模块标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("提示模块内容不能为空");
        }
    }
}
