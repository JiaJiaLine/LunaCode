package com.lunacode.team;

import com.lunacode.config.DefaultFeatureGateService;
import com.lunacode.config.FeatureConfig;
import com.lunacode.team.mailbox.TeamMessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTeamManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void createsRepoScopedTeamAndRegistersMemberName() {
        DefaultTeamManager manager = manager(Set.of("FORK_SUBAGENT"));

        TeamRecord team = manager.createTeam("core", "lead");
        TeamMemberRecord member = manager.addMember(new TeamMemberAddRequest(team.name(), "alice", "coder", TeamMemberBackendKind.SAME_PROCESS, Optional.empty(), false, Optional.of("agent-a"), "session"));

        assertEquals("core", manager.currentTeam().orElseThrow().name());
        assertEquals(TeamMemberLaunchMode.FORK, member.launchMode());
        assertEquals("agent-a", manager.registry("core").resolveName("alice").orElseThrow());
    }

    @Test
    void terminalBackendFailsExplicitly() {
        DefaultTeamManager manager = manager(Set.of());
        manager.createTeam("core", "lead");

        assertThrows(IllegalStateException.class, () -> manager.addMember(new TeamMemberAddRequest("core", "bob", "coder", TeamMemberBackendKind.TERMINAL, Optional.empty(), false, Optional.empty(), "session")));
    }

    @Test
    void sendMessageRoutesByNameAndRequiresSummary() {
        DefaultTeamManager manager = manager(Set.of());
        TeamRecord team = manager.createTeam("core", "lead");
        manager.addMember(new TeamMemberAddRequest(team.name(), "alice", "coder", TeamMemberBackendKind.SAME_PROCESS, Optional.of("reviewer"), false, Optional.of("agent-a"), "session"));

        assertThrows(IllegalArgumentException.class, () -> manager.sendMessage("core", "lead", "alice", TeamMessageType.TEXT, "too short", "hello", Map.of()));
        var sent = manager.sendMessage("core", "lead", "alice", TeamMessageType.TEXT, "interface signature changed today please review", "hello", Map.of());

        assertEquals("agent-a", sent.get(0).to());
        assertTrue(sent.get(0).summary().contains("interface"));
    }

    private DefaultTeamManager manager(Set<String> features) {
        TeamPaths paths = new TeamPaths(tempDir, tempDir.resolve("repo"));
        return new DefaultTeamManager(new JsonTeamStore(paths), null, new DefaultFeatureGateService(new FeatureConfig(features)));
    }
}
