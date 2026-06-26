package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GlobToolTest {
    @TempDir Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supportsRecursiveGlobAndEmptyResult() throws Exception {
        Files.createDirectories(tempDir.resolve("src/a/b"));
        Files.writeString(tempDir.resolve("src/a/b/C.java"), "class C {}");
        GlobTool tool = new GlobTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("pattern", "src/**/*.java"));
        ToolResult empty = tool.execute(context(), mapper.createObjectNode().put("pattern", "none/**/*.java"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("src/a/b/C.java"));
        assertFalse(empty.isError());
        assertEquals("", empty.content());
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(tempDir, Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }
}