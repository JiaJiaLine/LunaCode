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


    @Test
    void doubleStarSlashMatchesZeroDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("toolTest/nested"));
        Files.createDirectories(tempDir.resolve("src/toolTest/deep"));
        Files.writeString(tempDir.resolve("toolTest/hello.java"), "class RootHello {}");
        Files.writeString(tempDir.resolve("toolTest/nested/hello.java"), "class NestedHello {}");
        Files.writeString(tempDir.resolve("src/toolTest/hello.java"), "class ChildHello {}");
        Files.writeString(tempDir.resolve("src/toolTest/deep/hello.java"), "class DeepHello {}");
        GlobTool tool = new GlobTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("pattern", "**/toolTest/**/hello.java"));

        assertFalse(result.isError());
        assertEquals(String.join("\n",
                "src/toolTest/deep/hello.java",
                "src/toolTest/hello.java",
                "toolTest/hello.java",
                "toolTest/nested/hello.java"), result.content());
    }

    @Test
    void recursiveGlobMatchesFilesDirectlyUnderPrefix() throws Exception {
        Files.createDirectories(tempDir.resolve("src/a"));
        Files.writeString(tempDir.resolve("src/C.java"), "class C {}");
        Files.writeString(tempDir.resolve("src/a/D.java"), "class D {}");
        GlobTool tool = new GlobTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("pattern", "src/**/*.java"));

        assertFalse(result.isError());
        assertEquals(String.join("\n", "src/C.java", "src/a/D.java"), result.content());
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(tempDir, Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }
}