package com.lunacode.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageMetadata;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class JsonlSessionStore implements SessionStore {
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private static final DateTimeFormatter ID_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path sessionsDir;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final SecureRandom random;
    private final SessionTitleDeriver titleDeriver;

    public JsonlSessionStore(Path projectRoot) {
        this(projectRoot, Clock.systemDefaultZone(), new ObjectMapper(), new SecureRandom(), new SessionTitleDeriver());
    }

    JsonlSessionStore(Path projectRoot, Clock clock, ObjectMapper mapper, SecureRandom random, SessionTitleDeriver titleDeriver) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        this.sessionsDir = projectRoot.toAbsolutePath().normalize().resolve(".lunacode").resolve("sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.random = Objects.requireNonNull(random, "random");
        this.titleDeriver = Objects.requireNonNull(titleDeriver, "titleDeriver");
    }

    @Override
    public SessionId createSessionId() {
        for (int i = 0; i < 32; i++) {
            String timestamp = ID_TIME_FORMAT.format(LocalDateTime.now(clock));
            String suffix = "%04x".formatted(random.nextInt(0x1_0000));
            SessionId id = new SessionId(timestamp + "-" + suffix);
            if (!Files.exists(pathFor(id))) {
                return id;
            }
        }
        throw new IllegalStateException("无法生成唯一会话 ID");
    }

    @Override
    public Path pathFor(SessionId id) {
        return sessionsDir.resolve(id.value() + ".jsonl");
    }

    @Override
    public void append(SessionId id, ConversationMessageSnapshot message) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(message, "message");
        try {
            Files.createDirectories(sessionsDir);
            ObjectNode record = mapper.createObjectNode();
            record.put("role", roleName(message.role()));
            record.set("content", contentNode(message));
            record.put("ts", message.timestamp() == null ? Instant.now(clock).getEpochSecond() : message.timestamp().getEpochSecond());
            Files.writeString(
                    pathFor(id),
                    mapper.writeValueAsString(record) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new SessionStoreException("追加会话记录失败: " + id.value(), e);
        }
    }

    @Override
    public SessionLoadResult load(SessionId id) {
        Objects.requireNonNull(id, "id");
        Path path = pathFor(id);
        if (!Files.exists(path)) {
            SessionInfo info = new SessionInfo(id.value(), path, "新会话", 0, createdAt(id), createdAt(id), false);
            return new SessionLoadResult(id, info, List.of(), List.of("会话文件不存在: " + path));
        }
        return readSessionFile(id, path);
    }

    @Override
    public List<SessionInfo> listSessions() {
        if (!Files.exists(sessionsDir)) {
            return List.of();
        }
        try (var stream = Files.list(sessionsDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .flatMap(path -> parseId(path).stream())
                    .map(id -> load(id).info())
                    .sorted(Comparator.comparing(SessionInfo::lastActiveAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new SessionStoreException("扫描会话目录失败: " + sessionsDir, e);
        }
    }

    @Override
    public List<SessionInfo> deleteExpired(Duration ttl) {
        Duration effectiveTtl = ttl == null ? DEFAULT_TTL : ttl;
        List<SessionInfo> deleted = new ArrayList<>();
        for (SessionInfo info : listSessions()) {
            boolean expired = info.lastActiveAt().plus(effectiveTtl).isBefore(Instant.now(clock));
            if (!expired) {
                continue;
            }
            try {
                Files.deleteIfExists(info.path());
                deleted.add(info);
            } catch (IOException e) {
                throw new SessionStoreException("清理过期会话失败: " + info.path(), e);
            }
        }
        return List.copyOf(deleted);
    }

    public Path sessionsDir() {
        return sessionsDir;
    }

    private SessionLoadResult readSessionFile(SessionId id, Path path) {
        List<ConversationMessageSnapshot> messages = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new SessionStoreException("读取会话文件失败: " + path, e);
        }
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                JsonNode record = mapper.readTree(line);
                ConversationMessageSnapshot message = toSnapshot(id, lineNumber, record);
                messages.add(message);
            } catch (Exception e) {
                warnings.add("会话 " + id.value() + " 第 " + lineNumber + " 行已跳过: " + e.getMessage());
            }
        }
        Instant createdAt = createdAt(id);
        Instant lastActiveAt = messages.isEmpty() ? createdAt : messages.get(messages.size() - 1).timestamp();
        SessionInfo info = new SessionInfo(
                id.value(),
                path,
                titleDeriver.derive(messages),
                messages.size(),
                createdAt,
                lastActiveAt,
                lastActiveAt.plus(DEFAULT_TTL).isBefore(Instant.now(clock))
        );
        return new SessionLoadResult(id, info, messages, warnings);
    }

    private ConversationMessageSnapshot toSnapshot(SessionId id, int lineNumber, JsonNode record) {
        if (record == null || !record.isObject()) {
            throw new IllegalArgumentException("不是 JSON 对象");
        }
        JsonNode roleNode = record.get("role");
        JsonNode contentNode = record.get("content");
        JsonNode tsNode = record.get("ts");
        if (roleNode == null || contentNode == null || tsNode == null) {
            throw new IllegalArgumentException("缺少 role/content/ts 字段");
        }
        MessageRole role = parseRole(roleNode.asText());
        List<ContentBlock> blocks = parseContent(contentNode);
        String content = textOf(blocks);
        Instant timestamp = Instant.ofEpochSecond(tsNode.asLong());
        return new ConversationMessageSnapshot(
                id.value() + ":" + lineNumber + ":" + UUID.nameUUIDFromBytes((id.value() + lineNumber).getBytes()),
                role,
                MessageStatus.COMPLETE,
                timestamp,
                TokenUsage.unknown(),
                content,
                blocks,
                ConversationMessageMetadata.empty(),
                null
        );
    }

    private JsonNode contentNode(ConversationMessageSnapshot message) {
        List<ContentBlock> blocks = message.blocks();
        if (blocks.isEmpty()) {
            return TextNode.valueOf(message.content());
        }
        if (blocks.size() == 1 && blocks.get(0) instanceof ContentBlock.Text text) {
            return TextNode.valueOf(text.text());
        }
        ArrayNode array = mapper.createArrayNode();
        for (ContentBlock block : blocks) {
            if (block instanceof ContentBlock.Text text) {
                ObjectNode item = array.addObject();
                item.put("type", "text");
                item.put("text", text.text());
            } else if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                ObjectNode item = array.addObject();
                item.put("type", "tool_use");
                item.put("id", toolUse.id());
                item.put("name", toolUse.name());
                item.set("input", toolUse.input() == null ? JsonNodeFactory.instance.objectNode() : toolUse.input());
            } else if (block instanceof ContentBlock.ToolResultBlock toolResult) {
                ObjectNode item = array.addObject();
                item.put("type", "tool_result");
                item.put("tool_use_id", toolResult.toolUseId());
                item.put("content", toolResult.content());
                item.put("is_error", toolResult.isError());
            }
        }
        return array;
    }

    private List<ContentBlock> parseContent(JsonNode node) {
        if (node.isTextual()) {
            return node.asText().isBlank() ? List.of() : List.of(new ContentBlock.Text(node.asText()));
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("content 必须是字符串或数组");
        }
        List<ContentBlock> blocks = new ArrayList<>();
        for (JsonNode item : node) {
            String type = item.path("type").asText("");
            if ("text".equals(type)) {
                blocks.add(new ContentBlock.Text(item.path("text").asText("")));
            } else if ("tool_use".equals(type)) {
                blocks.add(new ContentBlock.ToolUseBlock(
                        item.path("id").asText(),
                        item.path("name").asText(),
                        item.path("input")
                ));
            } else if ("tool_result".equals(type)) {
                blocks.add(new ContentBlock.ToolResultBlock(
                        item.path("tool_use_id").asText(),
                        item.path("content").asText(""),
                        item.path("is_error").asBoolean(false)
                ));
            }
        }
        return List.copyOf(blocks);
    }

    private String textOf(List<ContentBlock> blocks) {
        StringBuilder out = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof ContentBlock.Text text) {
                out.append(text.text());
            } else if (block instanceof ContentBlock.ToolResultBlock result) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(result.content());
            }
        }
        return out.toString();
    }

    private MessageRole parseRole(String role) {
        return switch ((role == null ? "" : role).toLowerCase(Locale.ROOT)) {
            case "user" -> MessageRole.USER;
            case "assistant" -> MessageRole.ASSISTANT;
            case "tool" -> MessageRole.TOOL;
            case "system" -> MessageRole.SYSTEM;
            default -> throw new IllegalArgumentException("未知 role: " + role);
        };
    }

    private String roleName(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case SYSTEM -> "system";
        };
    }

    private List<SessionId> parseId(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".jsonl")) {
            return List.of();
        }
        try {
            return List.of(new SessionId(fileName.substring(0, fileName.length() - ".jsonl".length())));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private Instant createdAt(SessionId id) {
        String timestamp = id.value().substring(0, "yyyyMMdd-HHmmss".length());
        try {
            return LocalDateTime.parse(timestamp, ID_TIME_FORMAT)
                    .atZone(zone())
                    .toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now(clock);
        }
    }

    private ZoneId zone() {
        return clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone();
    }

    public static class SessionStoreException extends RuntimeException {
        public SessionStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
