package com.lunacode.skill;

import java.nio.file.Path;
import java.util.Optional;

public record SkillOrigin(
        SkillSourceKind kind,
        String sourceId,
        Optional<Path> path,
        int priority
) {
    public SkillOrigin {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        sourceId = sourceId == null ? "" : sourceId;
        path = path == null ? Optional.empty() : path.map(p -> p.toAbsolutePath().normalize());
    }
}
