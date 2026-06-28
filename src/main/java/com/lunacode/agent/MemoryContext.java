package com.lunacode.agent;

import java.util.List;

public record MemoryContext(List<String> userPreferences, List<String> projectFacts) {
    public MemoryContext {
        userPreferences = userPreferences == null ? List.of() : List.copyOf(userPreferences);
        projectFacts = projectFacts == null ? List.of() : List.copyOf(projectFacts);
    }
}
