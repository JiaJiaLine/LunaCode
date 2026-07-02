package com.lunacode.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class MarkdownMemoryStore implements MemoryStore {
    private static final String INDEX_FILE = "MemoryIndex.md";

    private final Path projectMemoryDir;
    private final Path userMemoryDir;
    private final MemoryIndexBuilder indexBuilder;

    public MarkdownMemoryStore(Path projectRoot) {
        this(projectRoot, Path.of(System.getProperty("user.home")), new MemoryIndexBuilder());
    }

    public MarkdownMemoryStore(Path projectRoot, Path userHome, MemoryIndexBuilder indexBuilder) {
        Path root = projectRoot == null ? Path.of("").toAbsolutePath().normalize() : projectRoot.toAbsolutePath().normalize();
        Path home = userHome == null ? Path.of(System.getProperty("user.home")) : userHome.toAbsolutePath().normalize();
        this.projectMemoryDir = root.resolve(".lunacode").resolve("memory");
        this.userMemoryDir = home.resolve(".lunacode").resolve("memory");
        this.indexBuilder = indexBuilder == null ? new MemoryIndexBuilder() : indexBuilder;
    }

    @Override
    public List<MemoryNote> listAll() {
        List<MemoryNote> notes = new ArrayList<>();
        notes.addAll(readDir(userMemoryDir));
        notes.addAll(readDir(projectMemoryDir));
        return List.copyOf(notes);
    }

    @Override
    public Optional<MemoryNote> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return listAll().stream().filter(note -> note.id().equals(id)).findFirst();
    }

    @Override
    public MemoryNote upsert(MemoryUpdateAction action, String sourceSession) {
        if (action.kind() == MemoryUpdateAction.ActionKind.NOOP) {
            return null;
        }
        MemoryType type = action.type();
        String id = action.targetId().filter(value -> !value.isBlank()).orElseGet(this::newId);
        Optional<MemoryNote> existing = find(id);
        Instant now = Instant.now();
        MemoryNote note = new MemoryNote(
                id,
                type,
                action.title().orElseGet(() -> existing.map(MemoryNote::title).orElse("未命名记忆")),
                existing.map(MemoryNote::createdAt).orElse(now),
                now,
                sourceSession,
                action.body().orElseGet(() -> existing.map(MemoryNote::body).orElse("")),
                pathFor(type, id)
        );
        write(note);
        return note;
    }

    @Override
    public boolean delete(String id) {
        Optional<MemoryNote> note = find(id);
        if (note.isEmpty()) {
            return false;
        }
        try {
            Files.deleteIfExists(note.get().path());
            return true;
        } catch (IOException e) {
            throw new MemoryStoreException("删除记忆失败: " + id, e);
        }
    }

    @Override
    public MemoryIndexSnapshot rebuildIndexes() {
        MemoryIndexSnapshot snapshot = indexBuilder.build(listAll());
        writeIndex(userMemoryDir, snapshot.userIndex());
        writeIndex(projectMemoryDir, snapshot.projectIndex());
        return snapshot;
    }

    @Override
    public MemoryIndexSnapshot loadIndexes() {
        String user = readIndex(userMemoryDir);
        String project = readIndex(projectMemoryDir);
        return indexBuilder.build(listAll().isEmpty() ? List.of() : listAll());
    }

    public Path projectMemoryDir() {
        return projectMemoryDir;
    }

    public Path userMemoryDir() {
        return userMemoryDir;
    }

    private List<MemoryNote> readDir(Path dir) {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !INDEX_FILE.equals(path.getFileName().toString()))
                    .flatMap(path -> parse(path).stream())
                    .toList();
        } catch (IOException e) {
            throw new MemoryStoreException("读取记忆目录失败: " + dir, e);
        }
    }

    private Optional<MemoryNote> parse(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.startsWith("---")) {
                return Optional.empty();
            }
            int end = content.indexOf("\n---", 3);
            if (end < 0) {
                return Optional.empty();
            }
            String frontmatter = content.substring(3, end).strip();
            String body = content.substring(end + 4).strip();
            Map<String, String> fields = parseFrontmatter(frontmatter);
            MemoryType type = MemoryType.fromValue(fields.get("type"));
            return Optional.of(new MemoryNote(
                    fields.get("id"),
                    type,
                    fields.get("title"),
                    Instant.parse(fields.get("created_at")),
                    Instant.parse(fields.get("updated_at")),
                    fields.get("source_session"),
                    body,
                    path
            ));
        } catch (RuntimeException | IOException e) {
            return Optional.empty();
        }
    }

    private Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontmatter.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            fields.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        return fields;
    }

    private void write(MemoryNote note) {
        try {
            Files.createDirectories(note.path().getParent());
            Files.writeString(note.path(), render(note), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MemoryStoreException("写入记忆失败: " + note.id(), e);
        }
    }

    private String render(MemoryNote note) {
        return """
                ---
                id: %s
                type: %s
                title: %s
                created_at: %s
                updated_at: %s
                source_session: %s
                ---

                %s
                """.formatted(
                note.id(),
                note.type().value(),
                singleLine(note.title()),
                note.createdAt(),
                note.updatedAt(),
                singleLine(note.sourceSession()),
                note.body()
        ).strip() + System.lineSeparator();
    }

    private Path pathFor(MemoryType type, String id) {
        Path dir = type.userLevel() ? userMemoryDir : projectMemoryDir;
        return dir.resolve(id + ".md");
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String singleLine(String value) {
        return value == null ? "" : value.replaceAll("\\R", " ").strip();
    }

    private void writeIndex(Path dir, String content) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(INDEX_FILE), content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MemoryStoreException("写入记忆索引失败: " + dir, e);
        }
    }

    private String readIndex(Path dir) {
        try {
            Path path = dir.resolve(INDEX_FILE);
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public static class MemoryStoreException extends RuntimeException {
        public MemoryStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
