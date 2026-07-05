package com.lunacode.skill;

import java.util.Optional;

public interface SkillPromptContextLoader {
    SkillPromptContext loadSummaries();

    Optional<LoadedSkillContext> currentLoadedSkill();
}
