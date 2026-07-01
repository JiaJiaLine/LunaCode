package com.lunacode.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContextConfigTest {
    @Test
    void defaultsCalculateBudgetFromSpecFormula() {
        ContextConfig config = ContextConfig.defaults();

        ContextBudget budget = config.budget();

        assertEquals(200_000, budget.contextWindowTokens());
        assertEquals(180_000, budget.effectiveWindowTokens());
        assertEquals(167_000, budget.autoCompactThresholdTokens());
        assertEquals(177_000, budget.forceCompactThresholdTokens());
        assertEquals(50_000, config.singleToolResultCharLimit());
        assertEquals(200_000, config.toolMessageCharLimit());
    }

    @Test
    void rejectsInvalidThresholdOrder() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new ContextConfig(
                10_000,
                9_000,
                2_000,
                1_000,
                50_000,
                200_000,
                10_000,
                5,
                5,
                5_000,
                25_000,
                3,
                3,
                0.2,
                Path.of(".lunacode", "tmp", "context")
        ));

        assertTrue(error.getMessage().contains("阈值"));
    }
}
