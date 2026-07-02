package com.lunacode.memory;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

public final class MemoryIndexBuilder {
    private static final int MAX_LINES = 200;
    private static final int MAX_BYTES = 25 * 1024;

    public MemoryIndexSnapshot build(List<MemoryNote> notes) {
        List<MemoryNote> safeNotes = notes == null ? List.of() : notes.stream()
                .sorted(Comparator.comparing(MemoryNote::updatedAt).reversed())
                .toList();
        String userIndex = render(safeNotes.stream().filter(note -> note.type().userLevel()).toList(), "用户级记忆");
        String projectIndex = render(safeNotes.stream().filter(note -> !note.type().userLevel()).toList(), "项目级记忆");
        String merged = trim(userIndex + "\n\n" + projectIndex);
        return new MemoryIndexSnapshot(userIndex, projectIndex, merged, lineCount(merged), merged.getBytes(StandardCharsets.UTF_8).length);
    }

    private String render(List<MemoryNote> notes, String title) {
        StringBuilder out = new StringBuilder("# ").append(title).append('\n');
        for (MemoryNote note : notes) {
            out.append("- [")
                    .append(note.id())
                    .append("] ")
                    .append(note.type().value())
                    .append(" | ")
                    .append(note.title())
                    .append(" | ")
                    .append(firstLine(note.body()))
                    .append('\n');
        }
        return trim(out.toString());
    }

    private String firstLine(String body) {
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "...";
    }

    private String trim(String content) {
        String[] lines = (content == null ? "" : content.strip()).split("\\R");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (lineCount(out.toString()) >= MAX_LINES) {
                break;
            }
            String candidate = out + line + "\n";
            if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                break;
            }
            out.append(line).append('\n');
        }
        return out.toString().strip();
    }

    private int lineCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.split("\\R").length;
    }
}
