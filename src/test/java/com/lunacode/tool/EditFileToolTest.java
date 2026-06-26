package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class EditFileToolTest {
    @TempDir Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void replacesOnlyUniqueMatch() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello world");
        EditFileTool tool = new EditFileTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("path", "a.txt").put("old_text", "world").put("new_text", "LunaCode"));

        assertFalse(result.isError());
        assertEquals("hello LunaCode", Files.readString(tempDir.resolve("a.txt")));
    }

    @Test
    void noMatchAndMultipleMatchesDoNotModifyFile() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello hello");
        EditFileTool tool = new EditFileTool(new WorkspacePathResolver(tempDir));

        ToolResult none = tool.execute(context(), mapper.createObjectNode().put("path", "a.txt").put("old_text", "missing").put("new_text", "x"));
        ToolResult many = tool.execute(context(), mapper.createObjectNode().put("path", "a.txt").put("old_text", "hello").put("new_text", "x"));

        assertTrue(none.isError());
        assertTrue(many.isError());
        assertEquals("hello hello", Files.readString(tempDir.resolve("a.txt")));
    }

    @Test
    void replacesMultilineLfInputInCrlfFile() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "alpha\r\nbeta\r\ngamma\r\n");
        EditFileTool tool = new EditFileTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode()
                .put("path", "a.txt")
                .put("old_text", "alpha\nbeta")
                .put("new_text", "alpha\nLunaCode"));

        assertFalse(result.isError(), result.content());
        assertEquals("alpha\r\nLunaCode\r\ngamma\r\n", Files.readString(tempDir.resolve("a.txt")));
        assertEquals(true, result.metadata().get("lineEndingAdjusted"));
    }
    private ToolExecutionContext context() {
        return new ToolExecutionContext(tempDir, Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }
}