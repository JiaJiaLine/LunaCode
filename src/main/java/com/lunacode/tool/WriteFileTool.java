package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WriteFileTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspacePathResolver resolver;
    private final JsonNode schema;

    public WriteFileTool(WorkspacePathResolver resolver) {
        this.resolver = resolver;
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("path", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties"))
                .set("content", MAPPER.createObjectNode().put("type", "string"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("path").add("content"));
    }

    @Override
    public String name() {
        return "WriteFile";
    }

    @Override
    public String description() {
        return "写入工作区内文本文件，会递归创建父目录并尽量设置目录 0755、文件 0644 权限。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        ValidationError validation = validateInput(input);
        if (validation != null) {
            return ToolResult.error(validation.message(), Map.of("errorType", "invalid_arguments", "code", validation.code()));
        }
        try {
            Path path = resolver.resolveInsideWorkspace(text(input, "path", "file_path"));
            String content = text(input, "content", "text", "contents");
            WriteSummary summary = writeText(path, content);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", resolver.relativize(path));
            metadata.put("bytesWritten", summary.bytesWritten());
            metadata.put("permissionsApplied", summary.permissionsApplied());
            metadata.put("lastModifiedTime", Files.getLastModifiedTime(path).toString());
            return ToolResult.success("写入成功: " + resolver.relativize(path) + " (" + summary.bytesWritten() + " bytes)", metadata);
        } catch (Exception e) {
            return ToolResult.error("写入文件失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Map.of("errorType", "write_file_error"));
        }
    }

    WriteSummary writeText(Path path, String content) throws Exception {
        Path parent = path.getParent();
        boolean permissionsApplied = false;
        if (parent != null) {
            permissionsApplied = createDirectoriesWithPermissions(parent);
        }
        Path temp = Files.createTempFile(parent == null ? path.toAbsolutePath().getParent() : parent, ".lunacode-", ".tmp");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(temp, bytes);
        permissionsApplied = applyPermissions(temp, false) || permissionsApplied;
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        permissionsApplied = applyPermissions(path, false) || permissionsApplied;
        return new WriteSummary(bytes.length, permissionsApplied);
    }

    private boolean createDirectoriesWithPermissions(Path directory) throws Exception {
        List<Path> missingDirectories = new ArrayList<>();
        Path current = directory;
        while (current != null && !Files.exists(current)) {
            missingDirectories.add(current);
            current = current.getParent();
        }
        Files.createDirectories(directory);
        boolean applied = applyPermissions(directory, true);
        for (Path missing : missingDirectories) {
            applied = applyPermissions(missing, true) || applied;
        }
        return applied;
    }

    private boolean applyPermissions(Path path, boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(path, permissions);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isDestructive() {
        return true;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return false;
    }

    @Override
    public String category() {
        return "file";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || text(input, "path", "file_path").isBlank()) {
            return new ValidationError("missing_path", "WriteFile 需要 path 参数");
        }
        if (!hasAny(input, "content", "text", "contents")) {
            return new ValidationError("missing_content", "WriteFile 需要 content 参数");
        }
        return null;
    }

    private String text(JsonNode input, String... names) {
        for (String name : names) {
            if (input.hasNonNull(name)) {
                return input.path(name).asText();
            }
        }
        return "";
    }

    private boolean hasAny(JsonNode input, String... names) {
        if (input == null) {
            return false;
        }
        for (String name : names) {
            if (input.has(name)) {
                return true;
            }
        }
        return false;
    }
    record WriteSummary(long bytesWritten, boolean permissionsApplied) {}
}
