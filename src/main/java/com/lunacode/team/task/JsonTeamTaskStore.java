package com.lunacode.team.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JsonTeamTaskStore implements TeamTaskStore {
    private final Path path;
    private final ObjectMapper mapper;
    private final Map<String, TeamTaskRecord> tasks = new LinkedHashMap<>();
    private int nextId = 1;

    public JsonTeamTaskStore(Path teamDirectory) {
        this(teamDirectory.resolve("tasks.json"), new ObjectMapper());
    }

    public JsonTeamTaskStore(Path path, ObjectMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        load();
    }

    @Override
    public synchronized TeamTaskRecord create(TaskCreateRequest request) {
        String id = String.valueOf(nextId++);
        TeamTaskRecord record = new TeamTaskRecord(id, request.title(), request.description(), TeamTaskStatus.TODO, request.assignee(), Set.of(), Set.of(), Instant.now(), Instant.now());
        tasks.put(id, record);
        save();
        return record;
    }

    @Override
    public synchronized Optional<TeamTaskRecord> get(String id) {
        return Optional.ofNullable(tasks.get(cleanId(id)));
    }

    @Override
    public synchronized List<TeamTaskRecord> list(TaskListFilter filter) {
        TaskListFilter safeFilter = filter == null ? TaskListFilter.all() : filter;
        return tasks.values().stream()
                .filter(task -> safeFilter.status().map(status -> status == task.status()).orElse(true))
                .filter(task -> safeFilter.assignee().map(assignee -> task.assignee().map(assignee::equals).orElse(false)).orElse(true))
                .filter(task -> !safeFilter.onlyClaimable() || task.claimable())
                .sorted(Comparator.comparingInt(task -> Integer.parseInt(task.id())))
                .toList();
    }

    @Override
    public synchronized TeamTaskRecord update(String id, TaskUpdatePatch patch, String actor) {
        String safeId = cleanId(id);
        TeamTaskRecord existing = tasks.get(safeId);
        if (existing == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }
        TaskUpdatePatch safePatch = patch == null ? TaskUpdatePatch.empty() : patch;
        TeamTaskRecord updated = existing;
        if (safePatch.claim()) {
            String claimer = actor == null ? "" : actor.strip();
            if (claimer.isBlank()) {
                throw new IllegalArgumentException("claim requires actor");
            }
            if (!updated.claimable()) {
                throw new IllegalStateException("task is not claimable: " + id);
            }
            updated = updated.withAssignee(Optional.of(claimer)).withStatus(TeamTaskStatus.IN_PROGRESS);
        }
        if (safePatch.status().isPresent()) {
            updated = updated.withStatus(safePatch.status().orElseThrow());
        }
        if (safePatch.clearAssignee()) {
            updated = updated.withAssignee(Optional.empty());
        } else if (safePatch.assignee().isPresent()) {
            updated = updated.withAssignee(safePatch.assignee());
        }
        tasks.put(safeId, updated);
        applyDependencies(safeId, safePatch.addBlocks(), safePatch.addBlockedBy());
        save();
        return tasks.get(safeId);
    }

    private void applyDependencies(String id, Set<String> addBlocks, Set<String> addBlockedBy) {
        for (String blockedId : normalizeIds(addBlocks)) {
            ensureTask(blockedId);
            TeamTaskRecord source = tasks.get(id);
            TeamTaskRecord target = tasks.get(blockedId);
            tasks.put(id, source.withDependencies(source.blockedBy(), plus(source.blocks(), blockedId)));
            tasks.put(blockedId, target.withDependencies(plus(target.blockedBy(), id), target.blocks()));
        }
        for (String blockerId : normalizeIds(addBlockedBy)) {
            ensureTask(blockerId);
            TeamTaskRecord source = tasks.get(id);
            TeamTaskRecord blocker = tasks.get(blockerId);
            tasks.put(id, source.withDependencies(plus(source.blockedBy(), blockerId), source.blocks()));
            tasks.put(blockerId, blocker.withDependencies(blocker.blockedBy(), plus(blocker.blocks(), id)));
        }
    }

    private void ensureTask(String id) {
        if (!tasks.containsKey(id)) {
            throw new IllegalArgumentException("task not found: " + id);
        }
    }

    private Set<String> plus(Set<String> values, String value) {
        LinkedHashSet<String> copy = new LinkedHashSet<>(values);
        copy.add(value);
        return Set.copyOf(copy);
    }

    private Set<String> normalizeIds(Set<String> ids) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().map(this::cleanId).forEach(result::add);
        }
        return result;
    }

    private String cleanId(String id) {
        String value = id == null ? "" : id.strip();
        if (value.isBlank() || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException("invalid task id: " + id);
        }
        return value;
    }

    private void load() {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            nextId = root.path("nextId").asInt(1);
            JsonNode taskNode = root.path("tasks");
            if (taskNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = taskNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    TeamTaskRecord task = readTask(entry.getValue());
                    tasks.put(task.id(), task);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to load team tasks: " + path, e);
        }
    }

    private void save() {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", 1);
        root.put("nextId", nextId);
        ObjectNode taskNode = root.putObject("tasks");
        tasks.forEach((id, task) -> taskNode.set(id, writeTask(task)));
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to save team tasks: " + path, e);
        }
    }

    private TeamTaskRecord readTask(JsonNode node) {
        return new TeamTaskRecord(
                text(node, "id"),
                text(node, "title"),
                text(node, "description"),
                enumValue(text(node, "status")),
                optionalText(node, "assignee"),
                stringSet(node.path("blockedBy")),
                stringSet(node.path("blocks")),
                Instant.parse(text(node, "createdAt")),
                Instant.parse(text(node, "updatedAt"))
        );
    }

    private ObjectNode writeTask(TeamTaskRecord task) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", task.id());
        node.put("title", task.title());
        node.put("description", task.description());
        node.put("status", task.status().name());
        task.assignee().ifPresent(value -> node.put("assignee", value));
        var blockedBy = node.putArray("blockedBy");
        task.blockedBy().forEach(blockedBy::add);
        var blocks = node.putArray("blocks");
        task.blocks().forEach(blocks::add);
        node.put("createdAt", task.createdAt().toString());
        node.put("updatedAt", task.updatedAt().toString());
        return node;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private Set<String> stringSet(JsonNode node) {
        if (!node.isArray()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        });
        return Set.copyOf(values);
    }

    private TeamTaskStatus enumValue(String value) {
        try {
            return value == null || value.isBlank() ? TeamTaskStatus.TODO : TeamTaskStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TeamTaskStatus.TODO;
        }
    }
}
