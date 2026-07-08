package com.lunacode.coordinator;

import com.lunacode.config.DefaultFeatureGateService;
import com.lunacode.config.FeatureConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCoordinatorModeResolverTest {
    @Test
    void requiresFeatureFlagAndEnvironmentOptIn() {
        assertFalse(resolver(Set.of(), Map.of(DefaultCoordinatorModeResolver.ENV_NAME, "1")).resolve().enabled());
        assertFalse(resolver(Set.of("COORDINATOR_MODE"), Map.of()).resolve().enabled());
        assertTrue(resolver(Set.of("COORDINATOR_MODE"), Map.of(DefaultCoordinatorModeResolver.ENV_NAME, "true")).resolve().enabled());
    }

    @Test
    void allowedToolsExcludeWriteAndEdit() {
        CoordinatorModeState state = resolver(Set.of("COORDINATOR_MODE"), Map.of(DefaultCoordinatorModeResolver.ENV_NAME, "yes")).resolve();

        assertTrue(state.allowedTools().contains("Agent"));
        assertTrue(state.allowedTools().contains("Bash"));
        assertFalse(state.allowedTools().contains("WriteFile"));
        assertFalse(state.allowedTools().contains("EditFile"));
        assertTrue(state.systemPrompt().contains("synthesize"));
    }

    private DefaultCoordinatorModeResolver resolver(Set<String> features, Map<String, String> env) {
        return new DefaultCoordinatorModeResolver(new DefaultFeatureGateService(new FeatureConfig(features)), env, new CoordinatorPromptContributor());
    }
}
