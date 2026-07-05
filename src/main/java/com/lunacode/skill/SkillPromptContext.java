package com.lunacode.skill;

import java.util.List;
import java.util.Optional;

public record SkillPromptContext(
        List<SkillSummary> summaries,
        Optional<LoadedSkillContext> loadedSkill
) {
    public SkillPromptContext {
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
        loadedSkill = loadedSkill == null ? Optional.empty() : loadedSkill;
    }

    public static SkillPromptContext empty() {
        return new SkillPromptContext(List.of(), Optional.empty());
    }

    public boolean isEmpty() {
        return summaries.isEmpty() && loadedSkill.isEmpty();
    }
}
