package com.lunacode.worktree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonWorktreeStateStoreTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsState() {
        JsonWorktreeStateStore store = new JsonWorktreeStateStore(tempDir);
        WorktreeRecord record = new WorktreeRecord(
                "feature/a",
                WorktreeKind.MANUAL,
                tempDir.resolve(".lunacode/worktrees/feature/a"),
                "worktree-feature+a",
                "base",
                "head",
                Optional.of("main"),
                Instant.parse("2026-07-07T00:00:00Z"),
                Instant.parse("2026-07-07T00:10:00Z"),
                List.of("warn")
        );

        store.save(new WorktreeState(Map.of(record.name(), record)));
        WorktreeState loaded = store.load();

        assertEquals(1, loaded.active().size());
        WorktreeRecord loadedRecord = loaded.active().get("feature/a");
        assertEquals(record.branchName(), loadedRecord.branchName());
        assertEquals(record.path(), loadedRecord.path());
        assertEquals(record.originalBranch(), loadedRecord.originalBranch());
        assertEquals(record.warnings(), loadedRecord.warnings());
    }

    @Test
    void missingAndBlankStateReturnEmpty() throws Exception {
        JsonWorktreeStateStore store = new JsonWorktreeStateStore(tempDir);
        assertTrue(store.load().active().isEmpty());

        Files.createDirectories(store.path().getParent());
        Files.writeString(store.path(), " \n", StandardCharsets.UTF_8);
        assertTrue(store.load().active().isEmpty());
    }

    @Test
    void invalidJsonThrows() throws Exception {
        JsonWorktreeStateStore store = new JsonWorktreeStateStore(tempDir);
        Files.createDirectories(store.path().getParent());
        Files.writeString(store.path(), "{bad", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, store::load);
    }
}
