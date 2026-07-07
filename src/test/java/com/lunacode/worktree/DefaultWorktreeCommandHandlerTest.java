package com.lunacode.worktree;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWorktreeCommandHandlerTest {
    @Test
    void createsListsEntersExitsAndRemoves() {
        FakeManager manager = new FakeManager();
        DefaultWorktreeCommandHandler handler = new DefaultWorktreeCommandHandler(manager);

        WorktreeCommandHandler.CommandResult created = handler.handle("/worktree create feature/a", false);
        assertEquals("idle", created.state());
        assertTrue(created.message().contains("worktree-feature+a"));

        WorktreeCommandHandler.CommandResult list = handler.handle("/worktree list", false);
        assertEquals("idle", list.state());
        assertTrue(list.message().contains("feature/a"));

        WorktreeCommandHandler.CommandResult entered = handler.handle("/worktree enter feature/a", false);
        assertEquals("idle", entered.state());
        assertTrue(entered.message().contains("已进入 Worktree"));

        WorktreeCommandHandler.CommandResult exited = handler.handle("/worktree exit", false);
        assertEquals("idle", exited.state());
        assertTrue(exited.message().contains("已退出 Worktree"));

        WorktreeCommandHandler.CommandResult removed = handler.handle("/worktree remove feature/a --discardChanges", false);
        assertEquals("idle", removed.state());
        assertTrue(removed.message().contains("已删除 Worktree"));
    }

    @Test
    void busyAllowsListButRejectsMutatingCommands() {
        FakeManager manager = new FakeManager();
        manager.create(WorktreeCreateRequest.manual("feature"));
        DefaultWorktreeCommandHandler handler = new DefaultWorktreeCommandHandler(manager);

        assertEquals("idle", handler.handle("/worktree list", true).state());
        WorktreeCommandHandler.CommandResult blocked = handler.handle("/worktree create other", true);

        assertEquals("warning", blocked.state());
        assertTrue(blocked.message().contains("当前忙碌"));
    }

    @Test
    void refusesRemoveWhenChangesExist() {
        FakeManager manager = new FakeManager();
        manager.create(WorktreeCreateRequest.manual("feature"));
        manager.changes = new WorktreeChanges(1, 0);
        DefaultWorktreeCommandHandler handler = new DefaultWorktreeCommandHandler(manager);

        WorktreeCommandHandler.CommandResult result = handler.handle("/worktree remove feature", false);

        assertEquals("warning", result.state());
        assertTrue(result.message().contains("set discardChanges=true"));
    }

    private static class FakeManager implements WorktreeManager {
        private final List<WorktreeRecord> records = new ArrayList<>();
        private WorktreeSession session;
        private WorktreeChanges changes = WorktreeChanges.CLEAN;

        @Override
        public WorktreeCreateResult create(WorktreeCreateRequest request) {
            WorktreeRecord record = record(request.name(), request.kind());
            records.removeIf(existing -> existing.name().equals(record.name()));
            records.add(record);
            return new WorktreeCreateResult(record, false, true, "created", List.of());
        }

        @Override
        public Optional<WorktreeRecord> find(String name) {
            return records.stream().filter(record -> record.name().equals(name)).findFirst();
        }

        @Override
        public List<WorktreeSnapshot> list() {
            return records.stream()
                    .map(record -> new WorktreeSnapshot(
                            record.name(),
                            record.kind(),
                            record.path(),
                            record.branchName(),
                            record.headCommit(),
                            session != null && session.worktreeName().equals(record.name()),
                            changes,
                            record.createdAt(),
                            record.lastUsedAt(),
                            record.warnings()
                    ))
                    .toList();
        }

        @Override
        public WorktreeSession enter(String name, String sessionId) {
            WorktreeRecord record = find(name).orElseThrow();
            session = new WorktreeSession(name, Path.of("."), record.path(), record.branchName(), Optional.of("main"), "head", sessionId, Instant.now());
            return session;
        }

        @Override
        public void exit() {
            session = null;
        }

        @Override
        public Optional<WorktreeSession> currentSession() {
            return Optional.ofNullable(session);
        }

        @Override
        public Path effectiveWorkDir() {
            return session == null ? Path.of(".").toAbsolutePath().normalize() : session.worktreePath();
        }

        @Override
        public WorktreeRemoveResult remove(WorktreeRemoveRequest request) {
            WorktreeRecord record = find(request.name()).orElseThrow();
            if (!request.discardChanges() && changes.hasChanges()) {
                return new WorktreeRemoveResult(record.name(), false, true, Optional.of(record.path()), Optional.of(record.branchName()), changes, "worktree has changes, set discardChanges=true to force", List.of());
            }
            records.removeIf(existing -> existing.name().equals(record.name()));
            return new WorktreeRemoveResult(record.name(), true, false, Optional.of(record.path()), Optional.of(record.branchName()), changes, "removed", List.of());
        }

        @Override
        public WorktreeCleanupResult cleanupExpired(WorktreeCleanupPolicy policy) {
            return new WorktreeCleanupResult(0, 0, 0, 0, List.of(), List.of(), List.of());
        }

        @Override
        public String generateAgentName() {
            return "agent-a1234567";
        }

        @Override
        public List<String> startupWarnings() {
            return List.of();
        }

        private WorktreeRecord record(String name, WorktreeKind kind) {
            return new WorktreeRecord(
                    name,
                    kind,
                    Path.of("worktrees").resolve(name).toAbsolutePath().normalize(),
                    "worktree-" + name.replace('/', '+'),
                    "base",
                    "head",
                    Optional.of("main"),
                    Instant.now(),
                    Instant.now(),
                    List.of()
            );
        }
    }
}
