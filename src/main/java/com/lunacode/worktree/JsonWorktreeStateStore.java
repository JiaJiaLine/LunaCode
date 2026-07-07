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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JsonWorktreeStateStore implements WorktreeStateStore {
    private static final String STATE_FILE = ".lunacode/worktree_state.json";

    private final Path path;
    private final ObjectMapper mapper;

    public JsonWorktreeStateStore(Path repoRoot) {
        this(repoRoot.resolve(STATE_FILE), new ObjectMapper());
    }

    public JsonWorktreeStateStore(Path path, ObjectMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    @Override
    public WorktreeState load() {
        if (!Files.isRegularFile(path)) {
            return WorktreeState.empty();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return WorktreeState.empty();
            }
            JsonNode root = mapper.readTree(json);
            JsonNode activeNode = root.path("active");
            Map<String, WorktreeRecord> active = new LinkedHashMap<>();
            if (activeNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = activeNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    active.put(entry.getKey(), readRecord(entry.getValue()));
                }
            }
            return new WorktreeState(active);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load worktree state: " + path, e);
        }
    }

    @Override
    public void save(WorktreeState state) {
        WorktreeState safeState = state == null ? WorktreeState.empty() : state;
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        ObjectNode active = root.putObject("active");
        safeState.active().forEach((name, record) -> active.set(name, writeRecord(record)));
        writeAtomically(root);
    }

    @Override
    public Path path() {
        return path;
    }

    private WorktreeRecord readRecord(JsonNode node) {
        return new WorktreeRecord(
                requiredText(node, "name"),
                WorktreeKind.valueOf(requiredText(node, "kind")),
                Path.of(requiredText(node, "path")),
                requiredText(node, "branchName"),
                requiredText(node, "baseRef"),
                requiredText(node, "headCommit"),
                optionalText(node, "originalBranch"),
                Instant.parse(requiredText(node, "createdAt")),
                Instant.parse(requiredText(node, "lastUsedAt")),
                readStringList(node.path("warnings"))
        );
    }

    private ObjectNode writeRecord(WorktreeRecord record) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", record.name());
        node.put("kind", record.kind().name());
        node.put("path", record.path().toString());
        node.put("branchName", record.branchName());
        node.put("baseRef", record.baseRef());
        node.put("headCommit", record.headCommit());
        record.originalBranch().ifPresent(value -> node.put("originalBranch", value));
        node.put("createdAt", record.createdAt().toString());
        node.put("lastUsedAt", record.lastUsedAt().toString());
        var warnings = node.putArray("warnings");
        record.warnings().forEach(warnings::add);
        return node;
    }

    private void writeAtomically(ObjectNode root) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
            moveReplacing(temp, path);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save worktree state: " + path, e);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.asText());
            }
        });
        return List.copyOf(values);
    }
}
