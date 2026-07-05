package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReadFileTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspacePathResolver resolver;
    private final JsonNode schema;

    public ReadFileTool(WorkspacePathResolver resolver) {
        this.resolver = resolver;
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("path", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties"))
                .set("offset", MAPPER.createObjectNode().put("type", "integer").put("minimum", 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties"))
                .set("limit", MAPPER.createObjectNode().put("type", "integer").put("minimum", 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("path"));
    }

    @Override
    public String name() {
        return "ReadFile";
    }

    @Override
    public String description() {
        return "读取工作区内文本文件，返回带原始行号的内容，可用 offset 和 limit 分段读取。";
    }

    @Override
    public JsonNode inputSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode input) {
        try {
            Path path = resolver.resolveInsideWorkspace(input.path("path").asText());
            if (!Files.exists(path)) {
                return ToolResult.error("文件不存在: " + resolver.relativize(path), Map.of("errorType", "file_not_found"));
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int offset = input.path("offset").asInt(1);
            int limit = input.path("limit").asInt(Math.max(1, context.maxContentChars() / 80));
            int startIndex = Math.min(offset - 1, lines.size());
            int endIndex = Math.min(lines.size(), startIndex + limit);
            StringBuilder content = new StringBuilder();
            for (int i = startIndex; i < endIndex; i++) {
                content.append(i + 1).append('\t').append(lines.get(i)).append('\n');
            }
            boolean truncated = endIndex < lines.size();
            String result = limitContent(context.masker().mask(content.toString()), context.maxContentChars());
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", resolver.relativize(path));
            metadata.put("startLine", lines.isEmpty() ? 0 : startIndex + 1);
            metadata.put("endLine", endIndex);
            metadata.put("totalLines", lines.size());
            metadata.put("fileSize", Files.size(path));
            metadata.put("lastModifiedTime", Files.getLastModifiedTime(path).toString());
            metadata.put("truncated", truncated || result.length() < content.length());
            return ToolResult.success(result, metadata);
        } catch (Exception e) {
            return ToolResult.error("读取文件失败: " + e.getMessage(), Map.of("errorType", "read_file_error"));
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isDestructive() {
        return false;
    }

    @Override
    public boolean isConcurrencySafe(JsonNode input) {
        return true;
    }

    @Override
    public String category() {
        return "file";
    }

    @Override
    public ValidationError validateInput(JsonNode input) {
        if (input == null || !input.hasNonNull("path") || input.path("path").asText().isBlank()) {
            return new ValidationError("missing_path", "ReadFile 需要 path 参数");
        }
        if (input.has("offset") && input.path("offset").asInt(0) < 1) {
            return new ValidationError("invalid_offset", "offset 必须从 1 开始");
        }
        if (input.has("limit") && input.path("limit").asInt(0) < 1) {
            return new ValidationError("invalid_limit", "limit 必须大于 0");
        }
        return null;
    }

    public static String limitContent(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content == null ? "" : content;
        }
        return content.substring(0, Math.max(0, maxChars)) + "\n[输出已截断]";
    }
}
