package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionScopeTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir Path tempDir;

    @Test
    void readAndWriteUseScopedWorkDir() throws Exception {
        Path main = tempDir.resolve("main");
        Path worktree = tempDir.resolve("worktree");
        Files.createDirectories(main);
        Files.createDirectories(worktree);
        Files.writeString(main.resolve("note.txt"), "main\n");
        Files.writeString(worktree.resolve("note.txt"), "worktree\n");
        WorkspacePathResolver resolver = new WorkspacePathResolver(main);
        ToolExecutionContext context = context(main);

        ToolResult read = ToolExecutionScopeHolder.withScope(new ToolExecutionScope(worktree), () ->
                new ReadFileTool(resolver).execute(context, mapper.createObjectNode().put("path", "note.txt"))
        );
        assertFalse(read.isError());
        assertTrue(read.content().contains("worktree"));
        assertFalse(read.content().contains("main"));

        ToolResult write = ToolExecutionScopeHolder.withScope(new ToolExecutionScope(worktree), () ->
                new WriteFileTool(resolver).execute(context, mapper.createObjectNode().put("path", "created.txt").put("content", "ok"))
        );
        assertFalse(write.isError());
        assertTrue(Files.exists(worktree.resolve("created.txt")));
        assertFalse(Files.exists(main.resolve("created.txt")));
    }

    @Test
    void bashUsesScopedWorkDir() throws Exception {
        Path main = tempDir.resolve("main");
        Path worktree = tempDir.resolve("worktree");
        Files.createDirectories(main);
        Files.createDirectories(worktree);
        ToolExecutionContext context = context(main);

        ToolResult result = ToolExecutionScopeHolder.withScope(new ToolExecutionScope(worktree), () ->
                new BashTool().execute(context, mapper.createObjectNode().put("command", "cd"))
        );

        assertFalse(result.isError(), result.content());
        String normalized = result.content().replace('\\', '/');
        assertTrue(normalized.contains(worktree.toString().replace('\\', '/')));
    }

    private ToolExecutionContext context(Path root) {
        return new ToolExecutionContext(root, Duration.ofSeconds(5), 10_000, new SensitiveValueMasker());
    }
}
