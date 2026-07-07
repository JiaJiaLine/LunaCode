package com.lunacode.worktree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWorktreeManagerTest {
    private static final Instant NOW = Instant.parse("2026-07-07T00:00:00Z");

    @TempDir Path tempDir;

    @Test
    void createsWorktreeAndPersistsRecord() {
        FakeGit git = new FakeGit();
        git.repositoryState = new GitRepositoryState(tempDir, "base", Optional.of("main"), true, " M a.txt");
        InMemoryStateStore stateStore = new InMemoryStateStore();
        DefaultWorktreeManager manager = manager(git, stateStore, new InMemorySessionStore(), List.of("init warning"));

        WorktreeCreateResult result = manager.create(WorktreeCreateRequest.manual("feature/a"));

        assertTrue(result.created());
        assertFalse(result.fastRestored());
        assertEquals("worktree-feature+a", result.record().branchName());
        assertEquals(tempDir.resolve(".lunacode/worktrees/feature/a").toAbsolutePath().normalize(), result.record().path());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("uncommitted changes")));
        assertTrue(result.warnings().contains("init warning"));
        assertEquals(1, git.inspectCount);
        assertEquals(1, git.addCount);
        assertEquals(result.record(), stateStore.state.active().get("feature/a"));
    }

    @Test
    void fastRestoresExistingWorktreeWithoutGitSubprocessOrInitialization() throws Exception {
        FakeGit git = new FakeGit();
        Path existing = tempDir.resolve(".lunacode/worktrees/feature");
        Files.createDirectories(existing);
        git.restoredHeads.put(existing.toAbsolutePath().normalize(), new FastRestoredHead(
                existing,
                tempDir.resolve(".git/worktrees/feature"),
                Optional.of("worktree-feature"),
                "restored-head",
                false
        ));
        DefaultWorktreeManager manager = manager(git, new InMemoryStateStore(), new InMemorySessionStore(), List.of("should not run"));

        WorktreeCreateResult result = manager.create(WorktreeCreateRequest.manual("feature"));

        assertTrue(result.fastRestored());
        assertFalse(result.created());
        assertEquals("restored-head", result.record().headCommit());
        assertEquals(0, git.inspectCount);
        assertEquals(0, git.addCount);
        assertEquals(0, git.initializerCalls);
    }

    @Test
    void entersAndExitsSessionWithoutChangingEffectiveRootPermanently() {
        FakeGit git = new FakeGit();
        git.repositoryState = new GitRepositoryState(tempDir, "origin-head", Optional.of("main"), false, "");
        WorktreeRecord record = record("feature", WorktreeKind.MANUAL, NOW);
        InMemoryStateStore stateStore = new InMemoryStateStore(new WorktreeState(Map.of(record.name(), record)));
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        DefaultWorktreeManager manager = manager(git, stateStore, sessionStore, List.of());

        WorktreeSession session = manager.enter("feature", "session-1");

        assertEquals(record.path(), manager.effectiveWorkDir());
        assertEquals("feature", session.worktreeName());
        assertEquals(Optional.of(session), sessionStore.session);

        manager.exit();

        assertEquals(tempDir.toAbsolutePath().normalize(), manager.effectiveWorkDir());
        assertTrue(manager.currentSession().isEmpty());
        assertTrue(sessionStore.session.isEmpty());
    }

    @Test
    void protectsRemovalWhenChangesExistUnlessDiscarded() {
        FakeGit git = new FakeGit();
        WorktreeRecord record = record("feature", WorktreeKind.MANUAL, NOW);
        git.changes.put(record.path(), new WorktreeChanges(1, 0));
        InMemoryStateStore stateStore = new InMemoryStateStore(new WorktreeState(Map.of(record.name(), record)));
        DefaultWorktreeManager manager = manager(git, stateStore, new InMemorySessionStore(), List.of());

        WorktreeRemoveResult kept = manager.remove(WorktreeRemoveRequest.manual("feature", false));

        assertTrue(kept.kept());
        assertFalse(kept.removed());
        assertEquals(0, git.removeCount);

        WorktreeRemoveResult removed = manager.remove(WorktreeRemoveRequest.manual("feature", true));

        assertTrue(removed.removed());
        assertEquals(1, git.removeCount);
        assertEquals(1, git.deleteBranchCount);
        assertFalse(stateStore.state.active().containsKey("feature"));
    }

    @Test
    void cleanupOnlyRemovesExpiredAutomaticCleanWorktrees() {
        FakeGit git = new FakeGit();
        WorktreeRecord manual = record("manual", WorktreeKind.MANUAL, NOW.minus(Duration.ofDays(2)));
        WorktreeRecord dirtyAgent = record("agent-a1234567", WorktreeKind.AGENT, NOW.minus(Duration.ofDays(2)));
        WorktreeRecord cleanAgent = record("agent-a7654321", WorktreeKind.AGENT, NOW.minus(Duration.ofDays(2)));
        git.changes.put(dirtyAgent.path(), new WorktreeChanges(1, 0));
        git.changes.put(cleanAgent.path(), WorktreeChanges.CLEAN);
        InMemoryStateStore stateStore = new InMemoryStateStore(new WorktreeState(Map.of(
                manual.name(), manual,
                dirtyAgent.name(), dirtyAgent,
                cleanAgent.name(), cleanAgent
        )));
        DefaultWorktreeManager manager = manager(git, stateStore, new InMemorySessionStore(), List.of());

        WorktreeCleanupResult result = manager.cleanupExpired(new WorktreeCleanupPolicy(Duration.ofHours(1), NOW, false));

        assertEquals(3, result.scanned());
        assertEquals(1, result.removed());
        assertEquals(1, result.kept());
        assertEquals(1, result.skipped());
        assertFalse(stateStore.state.active().containsKey(cleanAgent.name()));
        assertTrue(stateStore.state.active().containsKey(dirtyAgent.name()));
        assertTrue(stateStore.state.active().containsKey(manual.name()));
    }

    private DefaultWorktreeManager manager(
            FakeGit git,
            InMemoryStateStore stateStore,
            InMemorySessionStore sessionStore,
            List<String> initializerWarnings
    ) {
        WorktreeEnvironmentInitializer initializer = (repoRoot, worktreePath) -> {
            git.initializerCalls++;
            return initializerWarnings;
        };
        return new DefaultWorktreeManager(
                tempDir,
                new DefaultWorktreeNameValidator(),
                git,
                initializer,
                stateStore,
                sessionStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(new byte[]{1, 2, 3, 4})
        );
    }

    private WorktreeRecord record(String name, WorktreeKind kind, Instant lastUsedAt) {
        Path path = tempDir.resolve(".lunacode/worktrees").resolve(name).toAbsolutePath().normalize();
        return new WorktreeRecord(
                name,
                kind,
                path,
                "worktree-" + name.replace('/', '+'),
                "base",
                "base",
                Optional.of("main"),
                NOW.minus(Duration.ofDays(3)),
                lastUsedAt,
                List.of()
        );
    }

    private static class InMemoryStateStore implements WorktreeStateStore {
        private WorktreeState state;

        private InMemoryStateStore() {
            this(WorktreeState.empty());
        }

        private InMemoryStateStore(WorktreeState state) {
            this.state = state;
        }

        @Override
        public WorktreeState load() {
            return state;
        }

        @Override
        public void save(WorktreeState state) {
            this.state = state;
        }

        @Override
        public Path path() {
            return Path.of("memory");
        }
    }

    private static class InMemorySessionStore implements WorktreeSessionStore {
        private Optional<WorktreeSession> session = Optional.empty();

        @Override
        public Optional<WorktreeSession> load() {
            return session;
        }

        @Override
        public void save(Optional<WorktreeSession> session) {
            this.session = session == null ? Optional.empty() : session;
        }

        @Override
        public Path path() {
            return Path.of("memory");
        }
    }

    private static class FakeGit implements GitWorktreeClient {
        private GitRepositoryState repositoryState;
        private final Map<Path, FastRestoredHead> restoredHeads = new HashMap<>();
        private final Map<Path, WorktreeChanges> changes = new HashMap<>();
        private int inspectCount;
        private int addCount;
        private int removeCount;
        private int deleteBranchCount;
        private int initializerCalls;

        @Override
        public GitRepositoryState inspectRepository(Path repoRoot) {
            inspectCount++;
            return repositoryState == null
                    ? new GitRepositoryState(repoRoot, "base", Optional.of("main"), false, "")
                    : repositoryState;
        }

        @Override
        public Optional<FastRestoredHead> tryReadHead(Path worktreePath) {
            return Optional.ofNullable(restoredHeads.get(worktreePath.toAbsolutePath().normalize()));
        }

        @Override
        public void addWorktree(Path repoRoot, Path worktreePath, String branchName, String baseRef) {
            addCount++;
            try {
                Files.createDirectories(worktreePath);
                Files.writeString(worktreePath.resolve(".git"), "gitdir: fake\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public WorktreeChanges countChanges(Path worktreePath, String baseHeadCommit) {
            return changes.getOrDefault(worktreePath.toAbsolutePath().normalize(), WorktreeChanges.CLEAN);
        }

        @Override
        public void removeWorktree(Path repoRoot, Path worktreePath) {
            removeCount++;
        }

        @Override
        public void deleteBranch(Path repoRoot, String branchName) {
            deleteBranchCount++;
        }

        @Override
        public void configureHooksPath(Path worktreePath, Path hooksPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Path> ignoredFiles(Path repoRoot) {
            throw new UnsupportedOperationException();
        }
    }
}
