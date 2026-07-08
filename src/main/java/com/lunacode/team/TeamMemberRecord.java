package com.lunacode.team;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record TeamMemberRecord(
        String name,
        String agentId,
        String role,
        TeamMemberBackendKind backend,
        TeamMemberLaunchMode launchMode,
        Optional<String> agentType,
        boolean planModeRequired,
        Optional<String> worktreeName,
        Optional<Path> worktreePath,
        Optional<String> worktreeBranch,
        TeamMemberStatus status,
        Optional<Path> contextPath,
        Instant createdAt,
        Instant updatedAt
) {
    public TeamMemberRecord {
        name = requireText(name, "name");
        agentId = requireText(agentId, "agentId");
        role = role == null || role.isBlank() ? "teammate" : role.strip();
        backend = backend == null ? TeamMemberBackendKind.SAME_PROCESS : backend;
        launchMode = launchMode == null ? TeamMemberLaunchMode.GENERAL_PURPOSE : launchMode;
        agentType = agentType == null ? Optional.empty() : agentType.map(String::strip).filter(value -> !value.isBlank());
        worktreeName = worktreeName == null ? Optional.empty() : worktreeName.map(String::strip).filter(value -> !value.isBlank());
        worktreePath = worktreePath == null ? Optional.empty() : worktreePath.map(path -> path.toAbsolutePath().normalize());
        worktreeBranch = worktreeBranch == null ? Optional.empty() : worktreeBranch.map(String::strip).filter(value -> !value.isBlank());
        status = status == null ? TeamMemberStatus.IDLE : status;
        contextPath = contextPath == null ? Optional.empty() : contextPath.map(path -> path.toAbsolutePath().normalize());
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public TeamMemberRecord withAgentId(String newAgentId) {
        return new TeamMemberRecord(name, newAgentId, role, backend, launchMode, agentType, planModeRequired, worktreeName, worktreePath, worktreeBranch, status, contextPath, createdAt, Instant.now());
    }

    public TeamMemberRecord withStatus(TeamMemberStatus newStatus) {
        return new TeamMemberRecord(name, agentId, role, backend, launchMode, agentType, planModeRequired, worktreeName, worktreePath, worktreeBranch, newStatus, contextPath, createdAt, Instant.now());
    }

    public TeamMemberRecord withWorktree(String name, Path path, String branch) {
        return new TeamMemberRecord(this.name, agentId, role, backend, launchMode, agentType, planModeRequired, Optional.ofNullable(name), Optional.ofNullable(path), Optional.ofNullable(branch), status, contextPath, createdAt, Instant.now());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
