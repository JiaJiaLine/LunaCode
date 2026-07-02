package com.lunacode.prompt;

import java.nio.file.Path;
import java.util.Objects;

public record ProjectInstructionContext(Path sourcePath, String content) {
    public ProjectInstructionContext {
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        content = content == null ? "" : content;
    }

    public boolean isEmpty() {
        return content.isBlank();
    }

    public String render() {
        if (isEmpty()) {
            return "";
        }
        return """
                [项目指令]
                来源: %s
                %s
                """.formatted(sourcePath, content).strip();
    }
}