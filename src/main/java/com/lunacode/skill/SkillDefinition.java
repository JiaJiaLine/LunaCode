package com.lunacode.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record SkillDefinition(
        String name,
        String description,
        SkillExecutionMode mode,
        SkillContextPolicy context,
        Optional<String> model,
        List<String> tools,
        String body,
        SkillOrigin origin,
        Optional<Path> resourceRoot
) {
    public SkillDefinition {
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        mode = mode == null ? SkillExecutionMode.INLINE : mode;
        context = context == null ? SkillContextPolicy.FULL : context;
        model = model == null ? Optional.empty() : model.map(String::strip).filter(value -> !value.isBlank());
        tools = tools == null ? List.of() : List.copyOf(tools);
        body = body == null ? "" : body;
        if (origin == null) {
            throw new IllegalArgumentException("origin is required");
        }
        resourceRoot = resourceRoot == null ? Optional.empty() : resourceRoot.map(path -> path.toAbsolutePath().normalize());
    }

    public SkillSummary summary() {
        return new SkillSummary(name, description, origin, mode);
    }
}
