package com.lunacode.instructions;

import java.nio.file.Path;
import java.util.Objects;

public record InstructionSource(
        Path path,
        InstructionScope scope,
        int priority
) {
    public InstructionSource {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        scope = Objects.requireNonNull(scope, "scope");
    }
}
