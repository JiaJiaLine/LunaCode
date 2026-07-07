package com.lunacode.worktree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class DefaultWorktreeManager implements WorktreeManager {
    public static final String WORKTREES_DIR = ".lunacode/worktrees";

    private static final Pattern AUTO_AGENT_NAME = Pattern.compile("agent-a[0-9a-f]{6,7}");
    private static final Pattern AUTO_WORKFLOW_NAME = Pattern.compile("wf_[0-9a-f]{12}");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final Path repoRoot;
    private final Path worktreesRoot;
    private final WorktreeNameValidator nameValidator;
    private final GitWorktreeClient git;
    private final WorktreeEnvironmentInitializer initializer;
    private final WorktreeStateStore stateStore;
    private final WorktreeSessionStore sessionStore;
    private final Clock clock;
    private final SecureRandom random;
    private final List<String> startupWarnings = new ArrayList<>();

    private WorktreeState state;
    private WorktreeSession currentSession;

    public DefaultWorktreeManager(Path repoRoot, GitWorktreeClient git, WorktreeEnvironmentInitializer initializer) {
        this(
                repoRoot,
                new DefaultWorktreeNameValidator(),
                git,
                initializer,
                new JsonWorktreeStateStore(repoRoot),
                new JsonWorktreeSessionStore(repoRoot),
                Clock.systemUTC(),
                new SecureRandom()
        );
    }

    public DefaultWorktreeManager(
            Path repoRoot,
            WorktreeNameValidator nameValidator,
            GitWorktreeClient git,
            WorktreeEnvironmentInitializer initializer,
            WorktreeStateStore stateStore,
            WorktreeSessionStore sessionStore,
            Clock clock,
            SecureRandom random
    ) {
        this.repoRoot = Objects.requireNonNull(repoRoot, "repoRoot").toAbsolutePath().normalize();
        this.worktreesRoot = this.repoRoot.resolve(WORKTREES_DIR).normalize();
        this.nameValidator = Objects.requireNonNull(nameValidator, "nameValidator");
        this.git = Objects.requireNonNull(git, "git");
        this.initializer = initializer == null ? (root, path) -> List.of() : initializer;
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.random = random == null ? new SecureRandom() : random;
        this.state = this.stateStore.load();
        restoreSession();
    }

    @Override
    public synchronized WorktreeCreateResult create(WorktreeCreateRequest request) {
        Objects.requireNonNull(request, "request");
        ValidWorktreeName name = nameValidator.validate(request.name(), request.kind());
        Path worktreePath = worktreesRoot.resolve(name.relativePath()).normalize();
        ensureManagedPath(worktreePath);
        Instant now = now();

        if (Files.exists(worktreePath)) {
            FastRestoredHead restored = git.tryReadHead(worktreePath)
                    .orElseThrow(() -> new IllegalStateException("existing path is not a recoverable worktree: " + worktreePath));
            WorktreeRecord previous = state.active().get(name.rawName());
            WorktreeRecord record = new WorktreeRecord(
                    name.rawName(),
                    request.kind(),
                    worktreePath,
                    name.branchName(),
                    previous == null ? restored.headCommit() : previous.baseRef(),
                    restored.headCommit(),
                    previous == null ? Optional.empty() : previous.originalBranch(),
                    previous == null ? now : previous.createdAt(),
                    now,
                    previous == null ? List.of() : previous.warnings()
            );
            saveState(state.withRecord(record));
            return new WorktreeCreateResult(record, true, false, "reused existing worktree", record.warnings());
        }

        GitRepositoryState repoState = git.inspectRepository(repoRoot);
        List<String> warnings = new ArrayList<>();
        if (repoState.dirty()) {
            warnings.add("main repository has uncommitted changes; they are not included in the new worktree");
        }
        git.addWorktree(repoRoot, worktreePath, name.branchName(), repoState.headCommit());
        warnings.addAll(initializer.initialize(repoRoot, worktreePath));
        WorktreeRecord record = new WorktreeRecord(
                name.rawName(),
                request.kind(),
                worktreePath,
                name.branchName(),
                repoState.headCommit(),
                repoState.headCommit(),
                repoState.branchName(),
                now,
                now,
                warnings
        );
        saveState(state.withRecord(record));
        return new WorktreeCreateResult(record, false, true, "created worktree", warnings);
    }

    @Override
    public synchronized Optional<WorktreeRecord> find(String name) {
        return Optional.ofNullable(state.active().get(name));
    }

    @Override
    public synchronized List<WorktreeSnapshot> list() {
        return state.active().values().stream()
                .sorted(Comparator.comparing(WorktreeRecord::createdAt))
                .map(this::snapshot)
                .toList();
    }

    @Override
    public synchronized WorktreeSession enter(String name, String sessionId) {
        WorktreeRecord record = find(name).orElseThrow(() -> new IllegalArgumentException("not found: " + name));
        GitRepositoryState original = git.inspectRepository(repoRoot);
        WorktreeSession session = new WorktreeSession(
                record.name(),
                Path.of(System.getProperty("user.dir")),
                record.path(),
                record.branchName(),
                original.branchName(),
                original.headCommit(),
                sessionId == null || sessionId.isBlank() ? java.util.UUID.randomUUID().toString() : sessionId,
                now()
        );
        currentSession = session;
        sessionStore.save(Optional.of(session));
        saveState(state.withRecord(record.withLastUsedAt(now())));
        return session;
    }

    @Override
    public synchronized void exit() {
        currentSession = null;
        sessionStore.save(Optional.empty());
    }

    @Override
    public synchronized Optional<WorktreeSession> currentSession() {
        return Optional.ofNullable(currentSession);
    }

    @Override
    public synchronized Path effectiveWorkDir() {
        return currentSession == null ? repoRoot : currentSession.worktreePath();
    }

    @Override
    public synchronized WorktreeRemoveResult remove(WorktreeRemoveRequest request) {
        Objects.requireNonNull(request, "request");
        WorktreeRecord record = find(request.name())
                .orElseThrow(() -> new IllegalArgumentException("not found: " + request.name()));
        if (request.automaticCleanup() && record.kind() == WorktreeKind.MANUAL) {
            return kept(record, WorktreeChanges.CLEAN, "manual worktree is not removed by automatic cleanup", List.of());
        }
        WorktreeChanges changes = git.countChanges(record.path(), record.headCommit());
        if (!request.discardChanges() && changes.hasChanges()) {
            return kept(record, changes, "worktree has changes, set discardChanges=true to force", List.of());
        }
        git.removeWorktree(repoRoot, record.path());
        git.deleteBranch(repoRoot, record.branchName());
        if (currentSession != null && currentSession.worktreeName().equals(record.name())) {
            currentSession = null;
            sessionStore.save(Optional.empty());
        }
        saveState(state.without(record.name()));
        return new WorktreeRemoveResult(
                record.name(),
                true,
                false,
                Optional.of(record.path()),
                Optional.of(record.branchName()),
                changes,
                "removed worktree",
                List.of()
        );
    }

    @Override
    public synchronized WorktreeCleanupResult cleanupExpired(WorktreeCleanupPolicy policy) {
        WorktreeCleanupPolicy safePolicy = policy == null ? WorktreeCleanupPolicy.defaults() : policy;
        int scanned = 0;
        int removed = 0;
        int kept = 0;
        int skipped = 0;
        List<WorktreeRemoveResult> removals = new ArrayList<>();
        List<WorktreeSnapshot> keptWorktrees = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (WorktreeRecord record : List.copyOf(state.active().values())) {
            scanned++;
            if (!isCleanupCandidate(record, safePolicy)) {
                skipped++;
                continue;
            }
            WorktreeChanges changes;
            try {
                changes = git.countChanges(record.path(), record.headCommit());
            } catch (Exception e) {
                warnings.add("failed to inspect worktree changes for " + record.name() + ": " + e.getMessage());
                skipped++;
                continue;
            }
            if (changes.hasChanges() || safePolicy.dryRun()) {
                kept++;
                keptWorktrees.add(snapshot(record, changes));
                continue;
            }
            try {
                WorktreeRemoveResult result = remove(WorktreeRemoveRequest.automatic(record.name()));
                removals.add(result);
                if (result.removed()) {
                    removed++;
                } else {
                    kept++;
                }
            } catch (Exception e) {
                warnings.add("failed to cleanup worktree " + record.name() + ": " + e.getMessage());
                skipped++;
            }
        }

        return new WorktreeCleanupResult(scanned, removed, kept, skipped, removals, keptWorktrees, warnings);
    }

    @Override
    public synchronized String generateAgentName() {
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            suffix.append(HEX[random.nextInt(HEX.length)]);
        }
        return "agent-a" + suffix;
    }

    @Override
    public synchronized List<String> startupWarnings() {
        return List.copyOf(startupWarnings);
    }

    private void restoreSession() {
        try {
            Optional<WorktreeSession> restored = sessionStore.load();
            if (restored.isEmpty()) {
                return;
            }
            WorktreeSession session = restored.get();
            if (git.tryReadHead(session.worktreePath()).isPresent()) {
                currentSession = session;
            } else {
                sessionStore.save(Optional.empty());
                startupWarnings.add("ignored invalid worktree session: " + session.worktreePath());
            }
        } catch (Exception e) {
            startupWarnings.add("failed to restore worktree session: " + e.getMessage());
        }
    }

    private WorktreeSnapshot snapshot(WorktreeRecord record) {
        try {
            return snapshot(record, git.countChanges(record.path(), record.headCommit()));
        } catch (Exception e) {
            List<String> warnings = new ArrayList<>(record.warnings());
            warnings.add("failed to count changes: " + e.getMessage());
            return new WorktreeSnapshot(
                    record.name(),
                    record.kind(),
                    record.path(),
                    record.branchName(),
                    record.headCommit(),
                    isCurrent(record),
                    WorktreeChanges.CLEAN,
                    record.createdAt(),
                    record.lastUsedAt(),
                    warnings
            );
        }
    }

    private WorktreeSnapshot snapshot(WorktreeRecord record, WorktreeChanges changes) {
        return new WorktreeSnapshot(
                record.name(),
                record.kind(),
                record.path(),
                record.branchName(),
                record.headCommit(),
                isCurrent(record),
                changes,
                record.createdAt(),
                record.lastUsedAt(),
                record.warnings()
        );
    }

    private boolean isCurrent(WorktreeRecord record) {
        return currentSession != null && currentSession.worktreeName().equals(record.name());
    }

    private boolean isCleanupCandidate(WorktreeRecord record, WorktreeCleanupPolicy policy) {
        if (!record.kind().isAutomatic()) {
            return false;
        }
        if (!record.path().normalize().startsWith(worktreesRoot)) {
            return false;
        }
        if (!record.branchName().startsWith(DefaultWorktreeNameValidator.BRANCH_PREFIX)) {
            return false;
        }
        if (!AUTO_AGENT_NAME.matcher(record.name()).matches() && !AUTO_WORKFLOW_NAME.matcher(record.name()).matches()) {
            return false;
        }
        Instant expiresAt = record.lastUsedAt().plus(policy.ttl());
        return !expiresAt.isAfter(policy.now());
    }

    private WorktreeRemoveResult kept(WorktreeRecord record, WorktreeChanges changes, String message, List<String> warnings) {
        return new WorktreeRemoveResult(
                record.name(),
                false,
                true,
                Optional.of(record.path()),
                Optional.of(record.branchName()),
                changes,
                message,
                warnings
        );
    }

    private void saveState(WorktreeState newState) {
        state = newState;
        stateStore.save(state);
    }

    private void ensureManagedPath(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(worktreesRoot)) {
            throw new IllegalArgumentException("worktree path escapes managed root: " + path);
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
