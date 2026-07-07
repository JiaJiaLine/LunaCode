package com.lunacode.tool;

import java.nio.file.Path;
import java.util.Objects;

public record ToolExecutionScope(Path workDir) {
    public ToolExecutionScope {
        workDir = Objects.requireNonNull(workDir, "workDir").toAbsolutePath().normalize();
    }
}
