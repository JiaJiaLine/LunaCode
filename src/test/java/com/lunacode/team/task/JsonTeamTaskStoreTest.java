package com.lunacode.team.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTeamTaskStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void createsListsClaimsAndPersistsTasks() {
        JsonTeamTaskStore store = new JsonTeamTaskStore(tempDir);
        TeamTaskRecord task = store.create(new TaskCreateRequest("Data layer", "Refactor DAO", Optional.empty()));

        TeamTaskRecord claimed = store.update(task.id(), new TaskUpdatePatch(Optional.empty(), Optional.empty(), false, Set.of(), Set.of(), true), "alice");

        assertEquals(TeamTaskStatus.IN_PROGRESS, claimed.status());
        assertEquals("alice", claimed.assignee().orElseThrow());
        assertThrows(IllegalStateException.class, () -> store.update(task.id(), new TaskUpdatePatch(Optional.empty(), Optional.empty(), false, Set.of(), Set.of(), true), "bob"));
        assertEquals(1, new JsonTeamTaskStore(tempDir).list(TaskListFilter.all()).size());
    }

    @Test
    void addBlocksAndAddBlockedByMaintainBothDirections() {
        JsonTeamTaskStore store = new JsonTeamTaskStore(tempDir);
        TeamTaskRecord a = store.create(new TaskCreateRequest("A", "", Optional.empty()));
        TeamTaskRecord b = store.create(new TaskCreateRequest("B", "", Optional.empty()));
        TeamTaskRecord c = store.create(new TaskCreateRequest("C", "", Optional.empty()));

        store.update(a.id(), new TaskUpdatePatch(Optional.empty(), Optional.empty(), false, Set.of(b.id()), Set.of(), false), "lead");
        store.update(c.id(), new TaskUpdatePatch(Optional.empty(), Optional.empty(), false, Set.of(), Set.of(a.id()), false), "lead");

        assertTrue(store.get(a.id()).orElseThrow().blocks().contains(b.id()));
        assertTrue(store.get(b.id()).orElseThrow().blockedBy().contains(a.id()));
        assertTrue(store.get(c.id()).orElseThrow().blockedBy().contains(a.id()));
        assertFalse(store.get(c.id()).orElseThrow().claimable());
    }
}
