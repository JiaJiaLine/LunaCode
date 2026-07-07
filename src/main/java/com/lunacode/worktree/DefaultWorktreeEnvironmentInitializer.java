package com.lunacode.worktree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DefaultWorktreeEnvironmentInitializer implements WorktreeEnvironmentInitializer {
    private final GitWorktreeClient git;
    private final WorktreeSettingsLoader settingsLoader;
    private final WorktreeIncludeMatcher includeMatcher;

    public DefaultWorktreeEnvironmentInitializer(GitWorktreeClient git) {
        this(git, new WorktreeSettingsLoader(), new GitignoreWorktreeIncludeMatcher());
    }

    public DefaultWorktreeEnvironmentInitializer(
            GitWorktreeClient git,
            WorktreeSettingsLoader settingsLoader,
            WorktreeIncludeMatcher includeMatcher
    ) {
        this.git = Objects.requireNonNull(git, "git");
        this.settingsLoader = settingsLoader == null ? new WorktreeSettingsLoader() : settingsLoader;
        this.includeMatcher = includeMatcher == null ? new GitignoreWorktreeIncludeMatcher() : includeMatcher;
    }

    @Override
    public List<String> initialize(Path repoRoot, Path worktreePath) {
        Path root = normalize(repoRoot, "repoRoot");
        Path wtPath = normalize(worktreePath, "worktreePath");
        List<String> warnings = new ArrayList<>();
        WorktreeSettings settings = settingsLoader.load(root);
        warnings.addAll(settings.warnings());

        copyLocalConfigFiles(root, wtPath, settings.localConfigFiles(), warnings);
        configureHooks(root, wtPath, warnings);
        linkDirectories(root, wtPath, settings.symlinkDirectories(), warnings);
        copyIncludedIgnoredFiles(root, wtPath, warnings);

        return List.copyOf(warnings);
    }

    private void copyLocalConfigFiles(Path repoRoot, Path worktreePath, List<String> localConfigFiles, List<String> warnings) {
        for (String rawPath : localConfigFiles) {
            try {
                Path relative = safeRelativePath(rawPath);
                Path source = repoRoot.resolve(relative).normalize();
                Path target = worktreePath.resolve(relative).normalize();
                if (!Files.isRegularFile(source)) {
                    warnings.add("missing local config file: " + rawPath);
                    continue;
                }
                ensureUnder(worktreePath, target, rawPath);
                copyFile(source, target);
            } catch (Exception e) {
                warnings.add("failed to copy local config file " + rawPath + ": " + e.getMessage());
            }
        }
    }

    private void configureHooks(Path repoRoot, Path worktreePath, List<String> warnings) {
        Path hooksPath = null;
        Path husky = repoRoot.resolve(".husky");
        if (Files.isDirectory(husky)) {
            hooksPath = husky;
        } else {
            Path gitHooks = repoRoot.resolve(".git").resolve("hooks");
            if (Files.isDirectory(gitHooks)) {
                hooksPath = gitHooks;
            }
        }
        if (hooksPath == null) {
            return;
        }
        try {
            git.configureHooksPath(worktreePath, hooksPath);
        } catch (Exception e) {
            warnings.add("failed to configure git hooks: " + e.getMessage());
        }
    }

    private void linkDirectories(Path repoRoot, Path worktreePath, List<String> symlinkDirectories, List<String> warnings) {
        for (String rawPath : symlinkDirectories) {
            try {
                Path relative = safeRelativePath(rawPath);
                Path source = repoRoot.resolve(relative).normalize();
                Path target = worktreePath.resolve(relative).normalize();
                ensureUnder(repoRoot, source, rawPath);
                ensureUnder(worktreePath, target, rawPath);
                if (!Files.isDirectory(source)) {
                    warnings.add("symlink source directory not found: " + rawPath);
                    continue;
                }
                if (Files.exists(target)) {
                    continue;
                }
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.createSymbolicLink(target, source);
            } catch (Exception e) {
                warnings.add("failed to symlink directory " + rawPath + ": " + e.getMessage());
            }
        }
    }

    private void copyIncludedIgnoredFiles(Path repoRoot, Path worktreePath, List<String> warnings) {
        List<Path> ignoredFiles;
        try {
            ignoredFiles = git.ignoredFiles(repoRoot);
        } catch (Exception e) {
            warnings.add("failed to list ignored files: " + e.getMessage());
            return;
        }
        List<Path> includedFiles;
        try {
            includedFiles = includeMatcher.match(repoRoot, ignoredFiles);
        } catch (Exception e) {
            warnings.add("failed to match .worktreeinclude: " + e.getMessage());
            return;
        }
        for (Path includedFile : includedFiles) {
            String display = includedFile.toString();
            try {
                Path relative = safeRelativePath(display);
                Path source = repoRoot.resolve(relative).normalize();
                Path target = worktreePath.resolve(relative).normalize();
                ensureUnder(repoRoot, source, display);
                ensureUnder(worktreePath, target, display);
                if (!Files.exists(source)) {
                    warnings.add("included ignored file not found: " + display);
                    continue;
                }
                copyRecursively(source, target);
            } catch (Exception e) {
                warnings.add("failed to copy ignored include " + display + ": " + e.getMessage());
            }
        }
    }

    private Path safeRelativePath(String rawPath) {
        Objects.requireNonNull(rawPath, "rawPath");
        if (rawPath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (rawPath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("path must not contain backslash");
        }
        Path relative = Path.of(rawPath).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("path must stay inside repository: " + rawPath);
        }
        return relative;
    }

    private void ensureUnder(Path root, Path candidate, String display) {
        if (!candidate.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("path escapes root: " + display);
        }
    }

    private void copyFile(Path source, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.walk(source)) {
                for (Path current : stream.toList()) {
                    Path relative = source.relativize(current);
                    Path currentTarget = target.resolve(relative).normalize();
                    if (Files.isDirectory(current)) {
                        Files.createDirectories(currentTarget);
                    } else if (Files.isRegularFile(current)) {
                        copyFile(current, currentTarget);
                    }
                }
            }
        } else if (Files.isRegularFile(source)) {
            copyFile(source, target);
        }
    }

    private Path normalize(Path path, String field) {
        return Objects.requireNonNull(path, field).toAbsolutePath().normalize();
    }
}
