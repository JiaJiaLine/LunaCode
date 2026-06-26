package com.lunacode.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WriteFileToolTest {
    @TempDir Path tempDir;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsParentsWritesFileAndMetadata() throws Exception {
        WriteFileTool tool = new WriteFileTool(new WorkspacePathResolver(tempDir));
        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("path", "nested/deep/a.txt").put("content", "hello\n"));

        assertFalse(result.isError(), result.content());
        assertTrue(Files.isDirectory(tempDir.resolve("nested/deep")));
        assertEquals("hello\n", Files.readString(tempDir.resolve("nested/deep/a.txt")));
        assertEquals(6L, result.metadata().get("bytesWritten"));
        assertTrue(result.metadata().containsKey("permissionsApplied"));
    }

    @Test
    void overwritesExistingFileAtomicallyEnoughForCaller() throws Exception {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "old");
        WriteFileTool tool = new WriteFileTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("path", "a.txt").put("content", "new content"));

        assertFalse(result.isError(), result.content());
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void directExecuteRejectsMissingContent() {
        WriteFileTool tool = new WriteFileTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("path", "a.txt"));

        assertTrue(result.isError());
        assertEquals("invalid_arguments", result.metadata().get("errorType"));
        assertFalse(Files.exists(tempDir.resolve("a.txt")));
    }

    @Test
    void posixFileSystemsUseExpectedPermissions() throws Exception {
        WriteFileTool tool = new WriteFileTool(new WorkspacePathResolver(tempDir));
        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("path", "nested/a.txt").put("content", "hello"));
        Path file = tempDir.resolve("nested/a.txt");
        Path dir = tempDir.resolve("nested");

        if (supportsPosix(file)) {
            assertFalse(result.isError(), result.content());
            assertEquals(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ
            ), Files.getPosixFilePermissions(file));
            assertEquals(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            ), Files.getPosixFilePermissions(dir));
            assertEquals(true, result.metadata().get("permissionsApplied"));
        } else {
            assertEquals(false, result.metadata().get("permissionsApplied"));
        }
    }

    @Test
    void acceptsCommonPathAndContentAliases() throws Exception {
        WriteFileTool tool = new WriteFileTool(new WorkspacePathResolver(tempDir));

        ToolResult result = tool.execute(context(), mapper.createObjectNode().put("file_path", "alias/a.txt").put("text", "alias content"));

        assertFalse(result.isError(), result.content());
        assertEquals("alias content", Files.readString(tempDir.resolve("alias/a.txt")));
    }
    private boolean supportsPosix(Path path) {
        try {
            Files.getPosixFilePermissions(path);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(tempDir, Duration.ofSeconds(1), 10_000, new SensitiveValueMasker());
    }
}