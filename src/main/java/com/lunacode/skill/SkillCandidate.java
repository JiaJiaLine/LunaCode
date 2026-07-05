package com.lunacode.skill;

import java.nio.file.Path;
import java.util.Optional;

public record SkillCandidate(
        Kind kind,
        Optional<Path> path,
        Optional<String> resourceName,
        Optional<String> content,
        SkillOrigin origin
) {
    public enum Kind {
        SINGLE_FILE,
        DIRECTORY,
        BUILTIN
    }

    public SkillCandidate {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        path = path == null ? Optional.empty() : path.map(p -> p.toAbsolutePath().normalize());
        resourceName = resourceName == null ? Optional.empty() : resourceName;
        content = content == null ? Optional.empty() : content;
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
    }

    public static SkillCandidate singleFile(Path path, SkillOrigin origin) {
        return new SkillCandidate(Kind.SINGLE_FILE, Optional.of(path), Optional.empty(), Optional.empty(), origin);
    }

    public static SkillCandidate directory(Path path, SkillOrigin origin) {
        return new SkillCandidate(Kind.DIRECTORY, Optional.of(path), Optional.empty(), Optional.empty(), origin);
    }

    public static SkillCandidate builtin(String resourceName, String content, SkillOrigin origin) {
        return new SkillCandidate(Kind.BUILTIN, Optional.empty(), Optional.of(resourceName), Optional.of(content), origin);
    }
}
