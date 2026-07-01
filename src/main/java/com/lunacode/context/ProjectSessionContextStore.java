package com.lunacode.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.tool.SensitiveValueMasker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProjectSessionContextStore implements SessionContextStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path workspaceRoot;
    private final Path sessionDirectory;
    private final SensitiveValueMasker masker;
    private final AtomicInteger counter = new AtomicInteger();

    public ProjectSessionContextStore(Path workspaceRoot, Path sessionRoot, SensitiveValueMasker masker) {
        this(workspaceRoot, sessionRoot, UUID.randomUUID().toString(), masker);
    }

    public ProjectSessionContextStore(Path workspaceRoot, Path sessionRoot, String sessionId, SensitiveValueMasker masker) {
        this.workspaceRoot = (workspaceRoot == null ? Path.of("") : workspaceRoot).toAbsolutePath().normalize();
        Path root = sessionRoot == null ? Path.of(".lunacode", "tmp", "context") : sessionRoot;
        Path resolvedRoot = root.isAbsolute() ? root : this.workspaceRoot.resolve(root);
        this.sessionDirectory = resolvedRoot.resolve(safeName(sessionId)).toAbsolutePath().normalize();
        this.masker = masker == null ? new SensitiveValueMasker() : masker;
    }

    @Override
    public Path sessionDirectory() {
        ensureDirectory();
        return sessionDirectory;
    }

    @Override
    public Path writeToolResult(ExternalizedToolResultPayload payload) {
        ensureDirectory();
        String name = String.format(
                "%04d-%s-%s-tool-result.txt",
                counter.incrementAndGet(),
                safeName(payload.messageId()),
                safeName(payload.toolUseId())
        );
        Path path = sessionDirectory.resolve(name);
        try {
            Files.writeString(path, payload.content() == null ? "" : payload.content(), StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new ContextStorageException("写入外置工具结果失败: " + path, e);
        }
    }

    @Override
    public Path writeSessionLog(SessionLogSnapshot snapshot) {
        ensureDirectory();
        Path path = sessionDirectory.resolve("session.jsonl");
        try {
            StringBuilder out = new StringBuilder();
            for (ConversationMessageSnapshot message : snapshot.messages()) {
                out.append(mapper.writeValueAsString(messageNode(message))).append('\n');
            }
            ObjectNode index = mapper.createObjectNode();
            index.put("type", "externalized_tool_result_index");
            ArrayNode refs = mapper.createArrayNode();
            for (ExternalizedToolResultRef ref : snapshot.externalizedToolResults()) {
                refs.add(refNode(ref));
            }
            index.set("items", refs);
            out.append(mapper.writeValueAsString(index)).append('\n');
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new ContextStorageException("写入完整会话记录失败: " + path, e);
        }
    }

    @Override
    public Path writeCompactionMetadata(CompactionMetadata metadata) {
        ensureDirectory();
        Path path = sessionDirectory.resolve("compaction-" + Instant.now().toEpochMilli() + ".json");
        ObjectNode node = mapper.createObjectNode();
        node.put("trigger", metadata.trigger().name());
        node.put("compactedAt", metadata.compactedAt().toString());
        node.put("summarizedMessages", metadata.summarizedMessages());
        node.put("retainedMessages", metadata.retainedMessages());
        node.put("externalizedToolResults", metadata.externalizedToolResults());
        node.put("restoredFiles", metadata.restoredFiles());
        node.put("sessionLogPath", metadata.sessionLogPath() == null ? "" : metadata.sessionLogPath().toString());
        try {
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node), StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new ContextStorageException("写入压缩元数据失败: " + path, e);
        }
    }

    private ObjectNode messageNode(ConversationMessageSnapshot message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "message");
        node.put("id", message.id());
        node.put("role", message.role().name());
        node.put("status", message.status().name());
        node.put("timestamp", message.timestamp().toString());
        node.put("content", message.content());
        node.put("errorSummary", message.errorSummary());
        node.put("contextSummary", message.metadata().contextSummary());
        ArrayNode blocks = mapper.createArrayNode();
        for (ContentBlock block : message.blocks()) {
            blocks.add(blockNode(block));
        }
        node.set("blocks", blocks);
        ArrayNode refs = mapper.createArrayNode();
        for (ExternalizedToolResultRef ref : message.metadata().externalizedToolResults()) {
            refs.add(refNode(ref));
        }
        node.set("externalizedToolResults", refs);
        return node;
    }

    private ObjectNode blockNode(ContentBlock block) {
        ObjectNode node = mapper.createObjectNode();
        if (block instanceof ContentBlock.Text text) {
            node.put("type", "text");
            node.put("text", text.text());
        } else if (block instanceof ContentBlock.ToolUseBlock toolUse) {
            node.put("type", "tool_use");
            node.put("id", toolUse.id());
            node.put("name", toolUse.name());
            node.set("input", toolUse.input());
        } else if (block instanceof ContentBlock.ToolResultBlock toolResult) {
            node.put("type", "tool_result");
            node.put("toolUseId", toolResult.toolUseId());
            node.put("content", toolResult.content());
            node.put("isError", toolResult.isError());
        }
        return node;
    }

    private ObjectNode refNode(ExternalizedToolResultRef ref) {
        ObjectNode node = mapper.createObjectNode();
        node.put("messageId", ref.messageId());
        node.put("toolUseId", ref.toolUseId());
        node.put("toolName", ref.toolName());
        node.put("path", ref.path().toString());
        node.put("originalChars", ref.originalChars());
        node.put("previewChars", ref.previewChars());
        node.put("error", ref.error());
        return node;
    }

    public String maskedPreview(String content, int maxChars) {
        String masked = masker.mask(maskKnownSensitive(content == null ? "" : content));
        return masked.length() <= maxChars ? masked : masked.substring(0, Math.max(0, maxChars));
    }

    private String maskKnownSensitive(String text) {
        return text.replaceAll("(?i)(authorization\\s*[:=]\\s*)(\\S+)", "$1[MASKED]")
                .replaceAll("(?i)((api[_-]?key|token)\\s*[:=]\\s*)(\\S+)", "$1[MASKED]");
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(sessionDirectory);
        } catch (IOException e) {
            throw new ContextStorageException("创建会话临时目录失败: " + sessionDirectory, e);
        }
    }

    private String safeName(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value;
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static final class ContextStorageException extends RuntimeException {
        public ContextStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
