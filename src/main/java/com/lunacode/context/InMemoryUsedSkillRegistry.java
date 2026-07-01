package com.lunacode.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryUsedSkillRegistry implements UsedSkillRegistry {
    private final Map<String, UsedSkillDefinition> definitions = new LinkedHashMap<>();

    @Override
    public synchronized void markUsed(String name, String definition) {
        if (name == null || name.isBlank() || definition == null || definition.isBlank()) {
            return;
        }
        definitions.put(name, new UsedSkillDefinition(name, definition, Instant.now()));
    }

    @Override
    public synchronized List<UsedSkillDefinition> recentDefinitions(int tokenBudget, ContextTokenEstimator estimator) {
        List<UsedSkillDefinition> ordered = definitions.values().stream()
                .sorted(Comparator.comparing(UsedSkillDefinition::usedAt).reversed())
                .toList();
        List<UsedSkillDefinition> result = new ArrayList<>();
        long usedTokens = 0;
        for (UsedSkillDefinition definition : ordered) {
            long tokens = ContextText.estimateTokens(definition.definition());
            if (usedTokens + tokens > tokenBudget) {
                continue;
            }
            usedTokens += tokens;
            result.add(definition);
        }
        return List.copyOf(result);
    }
}
