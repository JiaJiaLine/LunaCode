package com.lunacode.worktree;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProcessGitWorktreeClientTest {
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final String SECOND_COMMIT = "abcdef0123456789abcdef0123456789abcdef01";

    @TempDir Path tempDir;

    @Test
    void inspectsRepositoryState() throws Exception {
        assumeGit();
        Path repo = initRepo("inspect");
        ProcessGitWorktreeClient client = new ProcessGitWorktreeClient(Duration.ofSeconds(10));

        GitRepositoryState clean = client.inspectRepository(repo);
        assertEquals(repo.toAbsolutePath().normalize(), clean.repoRoot());
        assertFalse(clean.dirty());
        assertTrue(clean.branchName().isPresent());

        Files.writeString(repo.resolve("scratch.txt"), "dirty\n", StandardCharsets.UTF_8);
        GitRepositoryState dirty = client.inspectRepository(repo);
        assertTrue(dirty.dirty());
        assertTrue(dirty.statusSummary().contains("scratch.txt"));
    }

    @Test
    void fastRestoresHeadFromLooseRef() throws Exception {
        Path worktree = fakeWorktree("loose");
        Path gitDir = tempDir.resolve("repo/.git/worktrees/loose");
        Path commonDir = tempDir.resolve("repo/.git");
        Files.createDirectories(commonDir.resolve("refs/heads"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/worktree-loose\n", StandardCharsets.UTF_8);
        Files.writeString(gitDir.resolve("commondir"), "../..\n", StandardCharsets.UTF_8);
        Files.writeString(commonDir.resolve("refs/heads/worktree-loose"), COMMIT + "\n", StandardCharsets.UTF_8);

        Optional<FastRestoredHead> restored = new ProcessGitWorktreeClient().tryReadHead(worktree);

        assertTrue(restored.isPresent());
        assertEquals("worktree-loose", restored.get().branchName().orElseThrow());
        assertEquals(COMMIT, restored.get().headCommit());
        assertFalse(restored.get().detached());
    }

    @Test
    void fastRestoresHeadFromPackedRefsAndDetachedHead() throws Exception {
        Path packedWorktree = fakeWorktree("packed");
        Path packedGitDir = tempDir.resolve("repo/.git/worktrees/packed");
        Path commonDir = tempDir.resolve("repo/.git");
        Files.writeString(packedGitDir.resolve("HEAD"), "ref: refs/heads/worktree-packed\n", StandardCharsets.UTF_8);
        Files.writeString(packedGitDir.resolve("commondir"), "../..\n", StandardCharsets.UTF_8);
        Files.writeString(commonDir.resolve("packed-refs"), "# pack-refs\n" + SECOND_COMMIT + " refs/heads/worktree-packed\n", StandardCharsets.UTF_8);

        Optional<FastRestoredHead> packed = new ProcessGitWorktreeClient().tryReadHead(packedWorktree);
        assertTrue(packed.isPresent());
        assertEquals(SECOND_COMMIT, packed.get().headCommit());

        Path detached = fakeWorktree("detached");
        Path detachedGitDir = tempDir.resolve("repo/.git/worktrees/detached");
        Files.writeString(detachedGitDir.resolve("HEAD"), COMMIT + "\n", StandardCharsets.UTF_8);

        Optional<FastRestoredHead> restoredDetached = new ProcessGitWorktreeClient().tryReadHead(detached);
        assertTrue(restoredDetached.isPresent());
        assertTrue(restoredDetached.get().detached());
        assertTrue(restoredDetached.get().branchName().isEmpty());
    }

    @Test
    void createsCountsAndRemovesWorktree() throws Exception {
        assumeGit();
        Path repo = initRepo("lifecycle");
        ProcessGitWorktreeClient client = new ProcessGitWorktreeClient(Duration.ofSeconds(15));
        String base = git(repo, "rev-parse", "HEAD").trim();
        Path worktree = repo.resolve(".lunacode/worktrees/feature-one");

        client.addWorktree(repo, worktree, "worktree-feature-one", base);
        assertTrue(Files.exists(worktree.resolve(".git")));
        assertEquals(WorktreeChanges.CLEAN, client.countChanges(worktree, base));

        Files.writeString(worktree.resolve("README.md"), "changed\n", StandardCharsets.UTF_8);
        WorktreeChanges dirty = client.countChanges(worktree, base);
        assertEquals(1, dirty.uncommitted());
        assertEquals(0, dirty.newCommits());

        git(worktree, "add", "README.md");
        git(worktree, "commit", "-m", "change readme");
        WorktreeChanges committed = client.countChanges(worktree, base);
        assertEquals(0, committed.uncommitted());
        assertEquals(1, committed.newCommits());

        client.removeWorktree(repo, worktree);
        client.deleteBranch(repo, "worktree-feature-one");
        assertFalse(Files.exists(worktree));
        assertTrue(git(repo, "branch", "--list", "worktree-feature-one").isBlank());
    }

    @Test
    void configuresHooksPathAndListsIgnoredFiles() throws Exception {
        assumeGit();
        Path repo = initRepo("hooks");
        Files.writeString(repo.resolve(".gitignore"), "ignored.txt\nignored-dir/\n", StandardCharsets.UTF_8);
        git(repo, "add", ".gitignore");
        git(repo, "commit", "-m", "add gitignore");
        Files.writeString(repo.resolve("ignored.txt"), "secret\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("ignored-dir"));
        Files.writeString(repo.resolve("ignored-dir/a.txt"), "secret\n", StandardCharsets.UTF_8);

        ProcessGitWorktreeClient client = new ProcessGitWorktreeClient(Duration.ofSeconds(15));
        String base = git(repo, "rev-parse", "HEAD").trim();
        Path worktree = repo.resolve(".lunacode/worktrees/hooks");
        client.addWorktree(repo, worktree, "worktree-hooks", base);

        Path hooksPath = repo.resolve(".husky");
        Files.createDirectories(hooksPath);
        client.configureHooksPath(worktree, hooksPath);
        assertEquals(hooksPath.toAbsolutePath().normalize().toString(), git(worktree, "config", "--get", "core.hooksPath").trim());

        List<String> ignored = client.ignoredFiles(repo).stream()
                .map(path -> path.toString().replace('\\', '/'))
                .toList();
        assertTrue(ignored.contains("ignored.txt"));
        assertTrue(ignored.contains("ignored-dir"));

        client.removeWorktree(repo, worktree);
        client.deleteBranch(repo, "worktree-hooks");
    }

    private Path fakeWorktree(String name) throws IOException {
        Path worktree = tempDir.resolve("worktrees").resolve(name);
        Path gitDir = tempDir.resolve("repo/.git/worktrees").resolve(name);
        Files.createDirectories(worktree);
        Files.createDirectories(gitDir);
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDir + "\n", StandardCharsets.UTF_8);
        return worktree;
    }

    private Path initRepo(String name) throws Exception {
        Path repo = tempDir.resolve(name);
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "test@example.com");
        git(repo, "config", "user.name", "Test User");
        Files.writeString(repo.resolve("README.md"), "hello\n", StandardCharsets.UTF_8);
        git(repo, "add", "README.md");
        git(repo, "commit", "-m", "initial");
        return repo;
    }

    private void assumeGit() {
        Assumptions.assumeTrue(hasGit(), "git is required for this test");
    }

    private boolean hasGit() {
        try {
            Process process = new ProcessBuilder("git", "--version").start();
            process.getOutputStream().close();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String git(Path cwd, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command(args));
        builder.directory(cwd.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_ASKPASS", "");
        Process process = builder.start();
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(15, TimeUnit.SECONDS), "git command timed out");
        assertEquals(0, process.exitValue(), "git command failed: " + stderr);
        return stdout;
    }

    private List<String> command(String... args) {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }
}
