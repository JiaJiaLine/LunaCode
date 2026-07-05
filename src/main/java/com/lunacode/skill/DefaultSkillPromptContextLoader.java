package com.lunacode.skill;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultSkillPromptContextLoader implements SkillPromptContextLoader {
    private final SkillCatalog catalog;
    private final AtomicReference<LoadedSkillContext> loadedSkill = new AtomicReference<>();

    public DefaultSkillPromptContextLoader(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public SkillPromptContext loadSummaries() {
        if (catalog == null) {
            return SkillPromptContext.empty();
        }
        return new SkillPromptContext(catalog.snapshot().summaries(), currentLoadedSkill());
    }

    @Override
    public Optional<LoadedSkillContext> currentLoadedSkill() {
        return Optional.ofNullable(loadedSkill.get());
    }

    public void setCurrentLoadedSkill(LoadedSkillContext context) {
        loadedSkill.set(context);
    }

    public void clearCurrentLoadedSkill() {
        loadedSkill.set(null);
    }
}
