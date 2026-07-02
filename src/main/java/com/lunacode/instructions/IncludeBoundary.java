package com.lunacode.instructions;

import java.nio.file.Path;
import java.util.Objects;

public record IncludeBoundary(Path root, String description) {
    public IncludeBoundary {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        description = description == null || description.isBlank() ? root.toString() : description;
    }
}
