package com.lunacode.team.mailbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonTeamMailboxStore implements TeamMailboxStore {
    private static final Duration STALE_LOCK_AFTER = Duration.ofSeconds(30);

    private final Path mailboxDir;
    private final ObjectMapper mapper;

    public JsonTeamMailboxStore(Path teamDirectory) {
        this(teamDirectory.resolve("mailboxes"), new ObjectMapper());
    }

    public JsonTeamMailboxStore(Path mailboxDir, ObjectMapper mapper) {
        this.mailboxDir = mailboxDir.toAbsolutePath().normalize();
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public TeamMessageRecord append(String mailboxId, TeamMessageRecord message) {
        String safeMailboxId = safeMailboxId(mailboxId);
        TeamMessageRecord safeMessage = new TeamMessageRecord(
                message == null ? null : message.id(),
                message == null ? TeamMessageType.TEXT : message.type(),
                message == null ? "" : message.from(),
                safeMailboxId,
                message == null ? "" : message.summary(),
                message == null ? "" : message.message(),
                message == null ? Instant.now() : message.timestamp(),
                message != null && message.read(),
                message == null ? Map.of() : message.metadata()
        );
        withLock(safeMailboxId, () -> {
            try {
                Files.createDirectories(mailboxDir);
                Files.writeString(mailboxFile(safeMailboxId), mapper.writeValueAsString(writeMessage(safeMessage)) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("failed to append mailbox: " + safeMailboxId, e);
            }
        });
        return safeMessage;
    }

    @Override
    public List<TeamMessageRecord> read(String mailboxId) {
        String safeMailboxId = safeMailboxId(mailboxId);
        Path file = mailboxFile(safeMailboxId);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<TeamMessageRecord> messages = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    messages.add(readMessage(mapper.readTree(line)));
                }
            }
            return List.copyOf(messages);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read mailbox: " + safeMailboxId, e);
        }
    }

    private void withLock(String mailboxId, Runnable action) {
        Path lock = mailboxFile(mailboxId).resolveSibling(mailboxId + ".lock");
        for (int i = 0; i < 20; i++) {
            try {
                Files.createDirectories(mailboxDir);
                Files.createFile(lock);
                try {
                    action.run();
                } finally {
                    Files.deleteIfExists(lock);
                }
                return;
            } catch (FileAlreadyExistsException e) {
                recoverStaleLock(lock);
                sleep(25L + i * 10L);
            } catch (IOException e) {
                throw new IllegalStateException("failed to lock mailbox: " + mailboxId, e);
            }
        }
        throw new IllegalStateException("failed to acquire mailbox lock: " + mailboxId);
    }

    private void recoverStaleLock(Path lock) {
        try {
            if (!Files.exists(lock)) {
                return;
            }
            Instant modified = Files.getLastModifiedTime(lock).toInstant();
            if (modified.plus(STALE_LOCK_AFTER).isBefore(Instant.now())) {
                Files.deleteIfExists(lock);
            }
        } catch (IOException ignored) {
            // 下一轮重试会再次处理。
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for mailbox lock", e);
        }
    }

    private Path mailboxFile(String mailboxId) {
        Path path = mailboxDir.resolve(mailboxId + ".jsonl").normalize();
        if (!path.startsWith(mailboxDir)) {
            throw new IllegalArgumentException("mailbox path escapes managed root");
        }
        return path;
    }

    private String safeMailboxId(String value) {
        String id = value == null ? "" : value.strip();
        if (id.isBlank() || id.contains("/") || id.contains("\\") || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("invalid mailbox id: " + value);
        }
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private ObjectNode writeMessage(TeamMessageRecord message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", message.id());
        node.put("type", message.type().name());
        node.put("from", message.from());
        node.put("to", message.to());
        node.put("summary", message.summary());
        node.put("message", message.message());
        node.put("timestamp", message.timestamp().toString());
        node.put("read", message.read());
        ObjectNode metadata = node.putObject("metadata");
        message.metadata().forEach(metadata::put);
        return node;
    }

    private TeamMessageRecord readMessage(JsonNode node) {
        Map<String, String> metadata = new LinkedHashMap<>();
        JsonNode metadataNode = node.path("metadata");
        if (metadataNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = metadataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                metadata.put(entry.getKey(), entry.getValue().asText(""));
            }
        }
        return new TeamMessageRecord(
                text(node, "id"),
                type(text(node, "type")),
                text(node, "from"),
                text(node, "to"),
                text(node, "summary"),
                text(node, "message"),
                Instant.parse(text(node, "timestamp")),
                node.path("read").asBoolean(false),
                metadata
        );
    }

    private TeamMessageType type(String value) {
        try {
            return value == null || value.isBlank() ? TeamMessageType.TEXT : TeamMessageType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TeamMessageType.TEXT;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }
}
