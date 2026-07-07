package com.lunacode.worktree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWorktreeEnvironmentInitializerTest {
    @TempDir Path tempDir;

    @Test
    void initializesLocalConfigHooksSymlinksAndIgnoredIncludes() throws Exception {
        Path repo = tempDir.resolve("repo");
        Path worktree = tempDir.resolve("worktree");
        Files.createDirectories(repo.resolve(".git/hooks"));
        Files.createDirectories(worktree);
        Files.writeString(repo.resolve("settings.local.json"), """
                {
                  "settings": {
                    "worktree": {
                      "symlinkDirectories": ["deps"]
                    }
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("deps"));
        Files.writeString(repo.resolve("deps/lib.txt"), "shared\n", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve(".worktreeinclude"), ".env\nsecrets/\n", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve(".env"), "TOKEN=1\n", StandardCharsets.UTF_8);
        Files.createDirectories(repo.resolve("secrets"));
        Files.writeString(repo.resolve("secrets/token.txt"), "secret\n", StandardCharsets.UTF_8);
        FakeGitWorktreeClient git = new FakeGitWorktreeClient(List.of(Path.of(".env"), Path.of("secrets")));
        DefaultWorktreeEnvironmentInitializer initializer = new DefaultWorktreeEnvironmentInitializer(git);

        List<String> warnings = initializer.initialize(repo, worktree);

        assertEquals(Files.readString(repo.resolve("settings.local.json")), Files.readString(worktree.resolve("settings.local.json")));
        assertEquals(repo.resolve(".git/hooks").toAbsolutePath().normalize(), git.configuredHooksPath);
        assertEquals("TOKEN=1\n", Files.readString(worktree.resolve(".env")));
        assertEquals("secret\n", Files.readString(worktree.resolve("secrets/token.txt")));
        assertTrue(Files.exists(worktree.resolve("deps")) || warnings.stream().anyMatch(warning -> warning.contains("failed to symlink directory deps")));
    }

    @Test
    void missingLocalConfigIsWarningOnly() throws Exception {
        Path repo = tempDir.resolve("repo");
        Path worktree = tempDir.resolve("worktree");
        Files.createDirectories(repo);
        Files.createDirectories(worktree);
        FakeGitWorktreeClient git = new FakeGitWorktreeClient(List.of());
        DefaultWorktreeEnvironmentInitializer initializer = new DefaultWorktreeEnvironmentInitializer(git);

        List<String> warnings = initializer.initialize(repo, worktree);

        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("missing local config file")));
    }

    private static class FakeGitWorktreeClient implements GitWorktreeClient {
        private final List<Path> ignoredFiles;
        private Path configuredHooksPath;

        private FakeGitWorktreeClient(List<Path> ignoredFiles) {
            this.ignoredFiles = new ArrayList<>(ignoredFiles);
        }

        @Override
        public GitRepositoryState inspectRepository(Path repoRoot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<FastRestoredHead> tryReadHead(Path worktreePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addWorktree(Path repoRoot, Path worktreePath, String branchName, String baseRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorktreeChanges countChanges(Path worktreePath, String baseHeadCommit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeWorktree(Path repoRoot, Path worktreePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteBranch(Path repoRoot, String branchName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void configureHooksPath(Path worktreePath, Path hooksPath) {
            configuredHooksPath = hooksPath.toAbsolutePath().normalize();
        }

        @Override
        public List<Path> ignoredFiles(Path repoRoot) {
            return List.copyOf(ignoredFiles);
        }
    }
}
