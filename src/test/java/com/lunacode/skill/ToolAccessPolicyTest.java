package com.lunacode.skill;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolAccessPolicyTest {
    @Test
    void deniedToolsWinOverAllowedAndAlwaysVisible() {
        ToolAccessPolicy policy = new ToolAccessPolicy(
                Optional.of(Set.of("ReadFile", "Bash")),
                Set.of("Agent"),
                Set.of("Bash", "Agent")
        );

        assertTrue(policy.allows("ReadFile"));
        assertFalse(policy.allows("Bash"));
        assertFalse(policy.allows("Agent"));
    }

    @Test
    void unrestrictedPolicyCanStillDenySpecificTools() {
        ToolAccessPolicy policy = ToolAccessPolicy.unrestricted(Set.of()).withDeniedTools(Set.of("WriteFile"));

        assertTrue(policy.allows("ReadFile"));
        assertFalse(policy.allows("WriteFile"));
    }
}