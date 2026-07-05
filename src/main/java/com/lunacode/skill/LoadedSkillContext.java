package com.lunacode.skill;

import java.nio.file.Path;
import java.util.Optional;

public record LoadedSkillContext(
        String skillName,
        String renderedPrompt,
        Optional<Path> resourceRoot
) {
    public LoadedSkillContext {
        skillName = skillName == null ? "" : skillName.strip();
        renderedPrompt = renderedPrompt == null ? "" : renderedPrompt;
        resourceRoot = resourceRoot == null ? Optional.empty() : resourceRoot.map(path -> path.toAbsolutePath().normalize());
    }
}
