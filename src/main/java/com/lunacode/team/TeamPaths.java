package com.lunacode.team;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TeamPaths {
    private final Path root;
    private final Path repoRoot;
    private final TeamNameValidator validator = new TeamNameValidator();

    public TeamPaths(Path userHome, Path repoRoot) {
        Path safeUserHome = userHome == null ? Path.of(System.getProperty("user.home")) : userHome;
        this.root = safeUserHome.toAbsolutePath().normalize()
                .resolve(".lunacode")
                .resolve("teams")
                .resolve(repoId(repoRoot));
        this.repoRoot = (repoRoot == null ? Path.of(".") : repoRoot).toAbsolutePath().normalize();
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public Path root() {
        return root;
    }

    public Path currentTeamFile() {
        return root.resolve("current_team.txt").normalize();
    }

    public Path teamDir(String teamName) {
        String safeName = validator.validate(teamName, "teamName");
        Path path = root.resolve(safeName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("team path escapes managed root");
        }
        return path;
    }

    private String repoId(Path repoRoot) {
        Path normalized = (repoRoot == null ? Path.of(".") : repoRoot).toAbsolutePath().normalize();
        String leaf = normalized.getFileName() == null ? "repo" : normalized.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        return leaf + "-" + sha256(normalized.toString()).substring(0, 12);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
