package com.lunacode.worktree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitignoreWorktreeIncludeMatcherTest {
    @TempDir Path tempDir;

    @Test
    void matchesGitignoreStylePatternsAndNegation() throws Exception {
        Files.writeString(tempDir.resolve(".worktreeinclude"), """
                # local runtime files
                .env
                logs/
                !logs/private.env
                /config/*.json
                **/secret?.txt
                """, StandardCharsets.UTF_8);
        GitignoreWorktreeIncludeMatcher matcher = new GitignoreWorktreeIncludeMatcher();

        List<String> matched = matcher.match(tempDir, List.of(
                        Path.of(".env"),
                        Path.of("logs"),
                        Path.of("logs/app.log"),
                        Path.of("logs/private.env"),
                        Path.of("config/app.json"),
                        Path.of("nested/secret1.txt"),
                        Path.of("nested/secret-long.txt"),
                        Path.of("other.txt")
                )).stream()
                .map(path -> path.toString().replace('\\', '/'))
                .toList();

        assertTrue(matched.contains(".env"));
        assertTrue(matched.contains("logs"));
        assertTrue(matched.contains("logs/app.log"));
        assertFalse(matched.contains("logs/private.env"));
        assertTrue(matched.contains("config/app.json"));
        assertTrue(matched.contains("nested/secret1.txt"));
        assertFalse(matched.contains("nested/secret-long.txt"));
        assertFalse(matched.contains("other.txt"));
    }

    @Test
    void missingIncludeFileMatchesNothing() {
        GitignoreWorktreeIncludeMatcher matcher = new GitignoreWorktreeIncludeMatcher();

        assertTrue(matcher.match(tempDir, List.of(Path.of(".env"))).isEmpty());
    }
}
