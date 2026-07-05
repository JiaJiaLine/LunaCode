package com.lunacode.hook;

import java.nio.file.Path;
import java.util.Objects;

public record HookSource(HookSourceLevel level, Path path) {
    public HookSource {
        level = Objects.requireNonNull(level, "level");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public String displayName() {
        return level.name().toLowerCase() + ":" + path;
    }
}
