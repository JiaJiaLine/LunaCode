package com.lunacode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.permission.PathIntent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditFileTool implements Tool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WorkspacePathResolver resolver;
    private final WriteFileTool writer;
    private final JsonNode schema;

    public EditFileTool(WorkspacePathResolver resolver) {
        this.resolver = resolver;
        this.writer = new WriteFileTool(resolver);
        this.schema = MAPPER.createObjectNode()
                .put("type", "object")
                .set("properties", MAPPER.createObjectNode()
                        .set("path", MAPPER.createObjectNode().put("type", "string")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties")).set("old_text", MAPPER.createObjectNode().put("type", "string"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema.path("properties")).set("new_text", MAPPER.createObjectNode().put("type", "string"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) schema).set("required", MAPPER.createArrayNode().add("path").add("old_text").add("new_text"));
    }

    @Override
    public String name() {
        return "EditFile";
    }

    @Override
    public String description() {
        return "在工作区内文本文件中做原文唯一匹配替换；匹配不到或匹配多次时不会修改文件。";
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
            Path path = resolver.resolveInsideWorkspace(text(input, "path", "file_path"), PathIntent.WRITE);
            if (!Files.exists(path)) {
                return ToolResult.error("文件不存在: " + resolver.relativize(path), Map.of("errorType", "file_not_found", "path", resolver.relativize(path)));
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String oldText = text(input, "old_text", "oldText", "old_str", "old_string");
            String newText = text(input, "new_text", "newText", "new_str", "new_string");
            Match match = findMatch(content, oldText);
            if (match.count() == 0) {
                return ToolResult.error(
                        "匹配不到待替换原文，文件未修改。提示：请先用 ReadFile 读取最新内容，并把 old_text 写成文件中连续、完整、唯一的一段文本。",
                        Map.of("errorType", "match_not_found", "matchCount", 0, "oldTextChars", oldText.length(), "fileChars", content.length())
                );
            }
            if (match.count() > 1) {
                return ToolResult.error(
                        "匹配不唯一，出现 " + match.count() + " 次，文件未修改。请扩大 old_text 范围，使它只出现一次。",
                        Map.of("errorType", "match_not_unique", "matchCount", match.count(), "oldTextChars", oldText.length())
                );
            }
            String replacement = match.lineEndingAdjusted() ? toCrlf(newText) : newText;
            String updated = content.substring(0, match.start()) + replacement + content.substring(match.end());
            writer.writeText(path, updated);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", resolver.relativize(path));
            metadata.put("matchCount", 1);
            metadata.put("changed", true);
            metadata.put("lineEndingAdjusted", match.lineEndingAdjusted());
            metadata.put("lastModifiedTime", Files.getLastModifiedTime(path).toString());
            return ToolResult.success("修改成功: " + resolver.relativize(path), metadata);
        } catch (Exception e) {
            return ToolResult.error("修改文件失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Map.of("errorType", "edit_file_error"));
        }
    }

    private Match findMatch(String content, String oldText) {
        List<Candidate> candidates = List.of(
                new Candidate(oldText, false),
                new Candidate(toCrlf(oldText), true),
                new Candidate(oldText.replace("\r\n", "\n"), false)
        );
        for (Candidate candidate : candidates) {
            if (candidate.text().isEmpty()) {
                continue;
            }
            int count = countOccurrences(content, candidate.text());
            if (count == 1) {
                int start = content.indexOf(candidate.text());
                return new Match(count, start, start + candidate.text().length(), candidate.lineEndingAdjusted());
            }
            if (count > 1) {
                return new Match(count, -1, -1, candidate.lineEndingAdjusted());
            }
        }
        return new Match(0, -1, -1, false);
    }

    private String toCrlf(String value) {
        return value.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    private String text(JsonNode input, String... names) {
        for (String name : names) {
            if (input.hasNonNull(name)) {
                return input.path(name).asText();
            }
        }
        return "";
    }

    private int countOccurrences(String content, String needle) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
            return new ValidationError("missing_path", "EditFile 需要 path 参数");
        }
        if (text(input, "old_text", "oldText", "old_str", "old_string").isEmpty()) {
            return new ValidationError("missing_old_text", "old_text 必须非空");
        }
        if (!hasAny(input, "new_text", "newText", "new_str", "new_string")) {
            return new ValidationError("missing_new_text", "EditFile 需要 new_text 参数");
        }
        return null;
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

    private record Candidate(String text, boolean lineEndingAdjusted) {}

    private record Match(int count, int start, int end, boolean lineEndingAdjusted) {}
}