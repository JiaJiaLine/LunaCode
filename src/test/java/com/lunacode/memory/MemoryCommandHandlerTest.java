package com.lunacode.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCommandHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void togglesAutoMemoryRuntimeState() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(tempDir.resolve("project"), tempDir.resolve("home"), new MemoryIndexBuilder());
        DefaultMemoryContextLoader loader = new DefaultMemoryContextLoader(store);
        MemoryRuntimeState runtimeState = new MemoryRuntimeState(true);
        MemoryCommandHandler handler = new MemoryCommandHandler(store, loader, runtimeState);

        MemoryCommandHandler.CommandResult off = handler.handle("/memory off");
        assertEquals("idle", off.state());
        assertFalse(runtimeState.autoUpdateEnabled());
        assertEquals("off", runtimeState.latestState());

        MemoryCommandHandler.CommandResult on = handler.handle("/memory on");
        assertEquals("idle", on.state());
        assertTrue(runtimeState.autoUpdateEnabled());
        assertEquals("on", runtimeState.latestState());
    }
}
