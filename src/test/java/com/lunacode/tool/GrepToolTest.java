package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GrepToolTest {
    @TempDir Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsPathLineColumnAndSkipsTargetAndBinary() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("src/A.java"), "class A { ChatProvider p; }\n");
        Files.writeString(tempDir.resolve("target/Generated.java"), "ChatProvider\n");
        Files.write(tempDir.resolve("src/blob.bin"), new byte[] {0, 1, 2});
        GrepTool tool = new GrepTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("pattern", "ChatProvider").put("path", "src"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("src/A.java:1:11"));
        assertFalse(result.content().contains("target"));
        assertEquals(1, result.metadata().get("matchCount"));
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(tempDir, Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }
}