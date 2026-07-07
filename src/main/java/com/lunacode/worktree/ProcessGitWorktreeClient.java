package com.lunacode.worktree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ProcessGitWorktreeClient implements GitWorktreeClient {
    private static final Pattern COMMIT_ID = Pattern.compile("[0-9a-fA-F]{40,64}");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final Duration timeout;

    public ProcessGitWorktreeClient() {
        this(DEFAULT_TIMEOUT);
    }

    public ProcessGitWorktreeClient(Duration timeout) {
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public GitRepositoryState inspectRepository(Path repoRoot) {
        Path root = normalize(repoRoot, "repoRoot");
        String headCommit = runGit(root, "rev-parse", "HEAD").stdout().trim();
        String branch = runGit(root, "branch", "--show-current").stdout().trim();
        String status = runGit(root, "status", "--porcelain").stdout().trim();
        return new GitRepositoryState(
                root,
                headCommit,
                branch.isBlank() ? Optional.empty() : Optional.of(branch),
                !status.isBlank(),
                status
        );
    }

    @Override
    public Optional<FastRestoredHead> tryReadHead(Path worktreePath) {
        Path wtPath = normalize(worktreePath, "worktreePath");
        try {
            Path gitDir = readGitDir(wtPath).orElse(null);
            if (gitDir == null) {
                return Optional.empty();
            }
            Path headFile = gitDir.resolve("HEAD");
            if (!Files.isRegularFile(headFile)) {
                return Optional.empty();
            }
            String head = Files.readString(headFile, StandardCharsets.UTF_8).trim();
            if (head.startsWith("ref: ")) {
                String refName = head.substring("ref: ".length()).trim();
                Optional<String> commit = readRefCommit(gitDir, refName);
                if (commit.isEmpty()) {
                    return Optional.empty();
                }
                Optional<String> branchName = refName.startsWith("refs/heads/")
                        ? Optional.of(refName.substring("refs/heads/".length()))
                        : Optional.empty();
                return Optional.of(new FastRestoredHead(wtPath, gitDir, branchName, commit.get(), false));
            }
            if (!COMMIT_ID.matcher(head).matches()) {
                return Optional.empty();
            }
            return Optional.of(new FastRestoredHead(wtPath, gitDir, Optional.empty(), head, true));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public void addWorktree(Path repoRoot, Path worktreePath, String branchName, String baseRef) {
        Path root = normalize(repoRoot, "repoRoot");
        Path wtPath = normalize(worktreePath, "worktreePath");
        requireText(branchName, "branchName");
        requireText(baseRef, "baseRef");
        try {
            if (wtPath.getParent() != null) {
                Files.createDirectories(wtPath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to create worktree parent: " + wtPath.getParent(), e);
        }
        runGit(root, "worktree", "add", "-B", branchName, wtPath.toString(), baseRef);
    }

    @Override
    public WorktreeChanges countChanges(Path worktreePath, String baseHeadCommit) {
        Path wtPath = normalize(worktreePath, "worktreePath");
        requireText(baseHeadCommit, "baseHeadCommit");
        String status = runGit(wtPath, "status", "--porcelain").stdout();
        int uncommitted = 0;
        for (String line : status.split("\\R")) {
            if (!line.isBlank()) {
                uncommitted++;
            }
        }
        String count = runGit(wtPath, "rev-list", "--count", baseHeadCommit + "..HEAD").stdout().trim();
        int newCommits = count.isBlank() ? 0 : Integer.parseInt(count);
        return new WorktreeChanges(uncommitted, newCommits);
    }

    @Override
    public void removeWorktree(Path repoRoot, Path worktreePath) {
        Path root = normalize(repoRoot, "repoRoot");
        Path wtPath = normalize(worktreePath, "worktreePath");
        runGit(root, "worktree", "remove", "--force", wtPath.toString());
    }

    @Override
    public void deleteBranch(Path repoRoot, String branchName) {
        Path root = normalize(repoRoot, "repoRoot");
        requireText(branchName, "branchName");
        sleepAfterWorktreeRemove();
        runGit(root, "branch", "-D", branchName);
    }

    @Override
    public void configureHooksPath(Path worktreePath, Path hooksPath) {
        Path wtPath = normalize(worktreePath, "worktreePath");
        Path hooks = Objects.requireNonNull(hooksPath, "hooksPath").toAbsolutePath().normalize();
        runGit(wtPath, "config", "core.hooksPath", hooks.toString());
    }

    @Override
    public List<Path> ignoredFiles(Path repoRoot) {
        Path root = normalize(repoRoot, "repoRoot");
        String stdout = runGit(root, "ls-files", "--others", "--ignored", "--exclude-standard", "--directory").stdout();
        List<Path> paths = new ArrayList<>();
        for (String line : stdout.split("\\R")) {
            if (!line.isBlank()) {
                paths.add(Path.of(line.trim()).normalize());
            }
        }
        return List.copyOf(paths);
    }

    private Optional<Path> readGitDir(Path worktreePath) throws IOException {
        Path gitPath = worktreePath.resolve(".git");
        if (Files.isDirectory(gitPath)) {
            return Optional.of(gitPath.toAbsolutePath().normalize());
        }
        if (!Files.isRegularFile(gitPath)) {
            return Optional.empty();
        }
        String pointer = Files.readString(gitPath, StandardCharsets.UTF_8).trim();
        String lower = pointer.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("gitdir:")) {
            return Optional.empty();
        }
        String rawPath = pointer.substring(pointer.indexOf(':') + 1).trim();
        if (rawPath.isBlank()) {
            return Optional.empty();
        }
        Path gitDir = Path.of(rawPath);
        if (!gitDir.isAbsolute()) {
            gitDir = worktreePath.resolve(gitDir);
        }
        return Optional.of(gitDir.toAbsolutePath().normalize());
    }

    private Optional<String> readRefCommit(Path gitDir, String refName) throws IOException {
        Path commonDir = readCommonDir(gitDir);
        Optional<String> loose = readLooseRef(commonDir, refName).or(() -> readLooseRef(gitDir, refName));
        if (loose.isPresent()) {
            return loose;
        }
        return readPackedRef(commonDir.resolve("packed-refs"), refName)
                .or(() -> readPackedRef(gitDir.resolve("packed-refs"), refName));
    }

    private Path readCommonDir(Path gitDir) throws IOException {
        Path commonDirFile = gitDir.resolve("commondir");
        if (!Files.isRegularFile(commonDirFile)) {
            return gitDir;
        }
        String rawPath = Files.readString(commonDirFile, StandardCharsets.UTF_8).trim();
        if (rawPath.isBlank()) {
            return gitDir;
        }
        Path commonDir = Path.of(rawPath);
        if (!commonDir.isAbsolute()) {
            commonDir = gitDir.resolve(commonDir);
        }
        return commonDir.toAbsolutePath().normalize();
    }

    private Optional<String> readLooseRef(Path dir, String refName) {
        Path refPath = dir.resolve(Path.of(refName)).normalize();
        if (!Files.isRegularFile(refPath)) {
            return Optional.empty();
        }
        try {
            String commit = Files.readString(refPath, StandardCharsets.UTF_8).trim();
            return COMMIT_ID.matcher(commit).matches() ? Optional.of(commit) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<String> readPackedRef(Path packedRefs, String refName) {
        if (!Files.isRegularFile(packedRefs)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(packedRefs, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("^")) {
                    continue;
                }
                int space = trimmed.indexOf(' ');
                if (space <= 0) {
                    continue;
                }
                String commit = trimmed.substring(0, space);
                String ref = trimmed.substring(space + 1).trim();
                if (refName.equals(ref) && COMMIT_ID.matcher(commit).matches()) {
                    return Optional.of(commit);
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private GitResult runGit(Path workDir, String... args) {
        Path cwd = normalize(workDir, "workDir");
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_ASKPASS", "");
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());
            boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                throw new IllegalStateException("git command timed out: " + commandSummary(args));
            }
            GitResult result = new GitResult(process.exitValue(), stdout.join(), stderr.join());
            if (result.exitCode() != 0) {
                throw new IllegalStateException("git command failed (" + result.exitCode() + "): "
                        + commandSummary(args) + "\n" + result.stderr().trim());
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("failed to run git command: " + commandSummary(args), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git command interrupted: " + commandSummary(args), e);
        }
    }

    private CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("failed to read git output", e);
            }
        });
    }

    private String commandSummary(String... args) {
        return "git " + String.join(" ", args);
    }

    private void sleepAfterWorktreeRemove() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for git lock release", e);
        }
    }

    private Path normalize(Path path, String field) {
        return Objects.requireNonNull(path, field).toAbsolutePath().normalize();
    }

    private String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private record GitResult(int exitCode, String stdout, String stderr) {
    }
}
