package com.lunacode.worktree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonWorktreeSessionStoreTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsAndClearsSession() {
        JsonWorktreeSessionStore store = new JsonWorktreeSessionStore(tempDir);
        WorktreeSession session = new WorktreeSession(
                "agent-a3f2b1c",
                tempDir.resolve("main"),
                tempDir.resolve(".lunacode/worktrees/agent-a3f2b1c"),
                "worktree-agent-a3f2b1c",
                Optional.of("main"),
                "head",
                "session-1",
                Instant.parse("2026-07-07T01:00:00Z")
        );

        store.save(Optional.of(session));
        Optional<WorktreeSession> loaded = store.load();

        assertTrue(loaded.isPresent());
        assertEquals(session.worktreeName(), loaded.get().worktreeName());
        assertEquals(session.worktreePath(), loaded.get().worktreePath());
        assertEquals(session.originalBranch(), loaded.get().originalBranch());

        store.save(Optional.empty());
        assertTrue(store.load().isEmpty());
        assertFalse(Files.exists(store.path()));
    }

    @Test
    void missingAndBlankSessionReturnEmpty() throws Exception {
        JsonWorktreeSessionStore store = new JsonWorktreeSessionStore(tempDir);
        assertTrue(store.load().isEmpty());

        Files.createDirectories(store.path().getParent());
        Files.writeString(store.path(), " \n", StandardCharsets.UTF_8);
        assertTrue(store.load().isEmpty());
    }

    @Test
    void invalidJsonThrows() throws Exception {
        JsonWorktreeSessionStore store = new JsonWorktreeSessionStore(tempDir);
        Files.createDirectories(store.path().getParent());
        Files.writeString(store.path(), "{bad", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, store::load);
    }
}
