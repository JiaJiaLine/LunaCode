package com.lunacode.team;

import com.lunacode.config.DefaultFeatureGateService;
import com.lunacode.config.FeatureConfig;
import com.lunacode.team.mailbox.TeamMessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        assertTrue(team.directory().startsWith(tempDir.resolve("repo").resolve(".lunacode").resolve("teams")));
    }

    @Test
    void migratesLegacyUserHomeTeamsToRepoLocalDirectory() throws Exception {
        Path repo = tempDir.resolve("repo");
        TeamPaths paths = new TeamPaths(tempDir, repo);
        Path legacyTeam = paths.legacyRoot().resolve("core");
        Files.createDirectories(legacyTeam);
        Files.writeString(paths.legacyRoot().resolve("current_team.txt"), "core", StandardCharsets.UTF_8);
        Files.writeString(legacyTeam.resolve("team.json"), """
                {
                  "version": 1,
                  "name": "core",
                  "repoRoot": "%s",
                  "createdAt": "2026-01-01T00:00:00Z",
                  "updatedAt": "2026-01-01T00:00:00Z",
                  "members": {}
                }
                """.formatted(repo.toAbsolutePath().normalize().toString().replace("\\", "\\\\")), StandardCharsets.UTF_8);

        JsonTeamStore store = new JsonTeamStore(paths);

        assertEquals("core", store.currentTeamName().orElseThrow());
        assertTrue(Files.isRegularFile(paths.teamDir("core").resolve("team.json")));
        assertTrue(store.load("core").isPresent());
    }

    @Test
    void memberNameCanBindToBackgroundTaskAfterCreation() {
        DefaultTeamManager manager = manager(Set.of());
        TeamRecord team = manager.createTeam("core", "lead");

        TeamMemberRecord member = manager.addMember(new TeamMemberAddRequest(team.name(), "alice", "coder", TeamMemberBackendKind.SAME_PROCESS, Optional.empty(), false, Optional.empty(), "session"));
        assertTrue(manager.registry("core").resolveName("alice").isEmpty());

        TeamMemberRecord registered = manager.registerMemberAgentId(team.name(), member.name(), "bg-123");

        assertEquals("bg-123", registered.agentId());
        assertEquals("bg-123", manager.registry("core").resolveName("alice").orElseThrow());
    }

    @Test
    void existingRegistryBindingCanBeRepairedWhenBackgroundTaskIdArrives() {
        DefaultTeamManager manager = manager(Set.of());
        TeamRecord team = manager.createTeam("core", "lead");
        TeamMemberRecord member = manager.addMember(new TeamMemberAddRequest(team.name(), "alice", "coder", TeamMemberBackendKind.SAME_PROCESS, Optional.empty(), false, Optional.of("agent-stale"), "session"));

        TeamMemberRecord registered = manager.registerMemberAgentId(team.name(), member.name(), "bg-456");

        assertEquals("bg-456", registered.agentId());
        assertEquals("bg-456", manager.registry("core").resolveName("alice").orElseThrow());
    }

    @Test
    void existingIdleMemberCanBeReusedByName() {
        DefaultTeamManager manager = manager(Set.of());
        TeamRecord team = manager.createTeam("core", "lead");
        TeamMemberRecord first = manager.addMember(new TeamMemberAddRequest(team.name(), "alice", "coder", TeamMemberBackendKind.SAME_PROCESS, Optional.empty(), false, Optional.of("agent-a"), "session"));

        TeamMemberRecord second = manager.addMember(new TeamMemberAddRequest(team.name(), "alice", "coder", TeamMemberBackendKind.SAME_PROCESS, Optional.empty(), false, Optional.empty(), "session"));

        assertEquals(first.agentId(), second.agentId());
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
