package com.lunacode.team.tool;

import com.lunacode.coordinator.CoordinatorModeState;
import com.lunacode.skill.ToolAccessPolicy;
import com.lunacode.team.TeamRuntimeContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamToolPolicyResolverTest {
    private final TeamToolPolicyResolver resolver = new TeamToolPolicyResolver();

    @Test
    void ordinaryAgentCanBootstrapTeamButNotCollaborateYet() {
        ToolAccessPolicy policy = resolver.resolve(null, TeamRuntimeContext.none(), CoordinatorModeState.disabled());

        assertTrue(policy.allows("TeamCreate"));
        assertFalse(policy.allows("TeamDelete"));
        assertFalse(policy.allows("TaskCreate"));
        assertFalse(policy.allows("SendMessage"));
        assertTrue(policy.allows("ReadFile"));
    }

    @Test
    void memberGetsCollaborationButNotTeamManagement() {
        ToolAccessPolicy policy = resolver.resolve(null, TeamRuntimeContext.member("core", "alice", "agent-a"), CoordinatorModeState.disabled());

        assertTrue(policy.allows("TaskList"));
        assertTrue(policy.allows("SendMessage"));
        assertFalse(policy.allows("TeamCreate"));
        assertTrue(policy.allows("WriteFile"));
    }

    @Test
    void coordinatorWhitelistWins() {
        CoordinatorModeState coordinator = new CoordinatorModeState(true, Set.of("Agent", "ReadFile", "Bash", "TaskList"), "prompt");
        ToolAccessPolicy policy = resolver.resolve(null, TeamRuntimeContext.lead("core"), coordinator);

        assertTrue(policy.allows("TaskList"));
        assertTrue(policy.allows("Bash"));
        assertFalse(policy.allows("WriteFile"));
    }
}

