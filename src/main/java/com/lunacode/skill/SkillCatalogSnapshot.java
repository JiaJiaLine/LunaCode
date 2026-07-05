package com.lunacode.skill;

import java.util.List;

public record SkillCatalogSnapshot(
        List<SkillSummary> summaries,
        List<SkillDiagnostic> diagnostics
) {
    public SkillCatalogSnapshot {
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
