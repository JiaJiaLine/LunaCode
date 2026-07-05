package com.lunacode.skill;

import java.util.List;
import java.util.Optional;

public interface SkillCatalog {
    SkillCatalogSnapshot snapshot();

    Optional<SkillDefinition> loadForExecution(String name);

    List<SkillDiagnostic> diagnostics();
}
