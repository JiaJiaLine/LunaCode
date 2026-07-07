package com.lunacode.worktree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

public class JsonWorktreeSessionStore implements WorktreeSessionStore {
    private static final String SESSION_FILE = ".lunacode/worktree_session.json";

    private final Path path;
    private final ObjectMapper mapper;

    public JsonWorktreeSessionStore(Path repoRoot) {
        this(repoRoot.resolve(SESSION_FILE), new ObjectMapper());
    }

    public JsonWorktreeSessionStore(Path path, ObjectMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public Optional<WorktreeSession> load() {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(json);
            return Optional.of(new WorktreeSession(
                    requiredText(root, "worktreeName"),
                    Path.of(requiredText(root, "originalCwd")),
                    Path.of(requiredText(root, "worktreePath")),
                    requiredText(root, "worktreeBranch"),
                    optionalText(root, "originalBranch"),
                    requiredText(root, "originalHeadCommit"),
                    requiredText(root, "sessionId"),
                    Instant.parse(requiredText(root, "enteredAt"))
            ));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load worktree session: " + path, e);
        }
    }

    @Override
    public void save(Optional<WorktreeSession> session) {
        if (session == null || session.isEmpty()) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException e) {
                throw new IllegalStateException("failed to clear worktree session: " + path, e);
            }
        }
        WorktreeSession value = session.get();
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        root.put("worktreeName", value.worktreeName());
        root.put("originalCwd", value.originalCwd().toString());
        root.put("worktreePath", value.worktreePath().toString());
        root.put("worktreeBranch", value.worktreeBranch());
        value.originalBranch().ifPresent(originalBranch -> root.put("originalBranch", originalBranch));
        root.put("originalHeadCommit", value.originalHeadCommit());
        root.put("sessionId", value.sessionId());
        root.put("enteredAt", value.enteredAt().toString());
        writeAtomically(root);
    }

    @Override
    public Path path() {
        return path;
    }

    private void writeAtomically(ObjectNode root) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to save worktree session: " + path, e);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("missing required field: " + field);
        }
        return value.asText();
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? Optional.of(value.asText()) : Optional.empty();
    }
}
