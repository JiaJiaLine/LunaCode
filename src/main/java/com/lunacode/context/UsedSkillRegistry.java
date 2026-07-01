package com.lunacode.context;

import java.util.List;

public interface UsedSkillRegistry {
    void markUsed(String name, String definition);

    List<UsedSkillDefinition> recentDefinitions(int tokenBudget, ContextTokenEstimator estimator);
}
