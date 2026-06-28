package com.lunacode.prompt;

import java.nio.file.Path;
import java.util.Objects;

public record ProjectInstructionContext(Path sourcePath, String content) {
    public ProjectInstructionContext {
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        content = content == null ? "" : content;
    }
}
