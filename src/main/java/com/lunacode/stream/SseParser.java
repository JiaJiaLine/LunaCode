package com.lunacode.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SseParser {
    private String event;
    private final List<String> dataLines = new ArrayList<>();

    public Optional<SseEvent> accept(String line) {
        if (line == null || line.isEmpty()) {
            return emitIfPresent();
        }
        if (line.startsWith(":")) {
            return Optional.empty();
        }
        int colon = line.indexOf(':');
        String field = colon >= 0 ? line.substring(0, colon) : line;
        String value = colon >= 0 ? stripSingleLeadingSpace(line.substring(colon + 1)) : "";
        switch (field) {
            case "event" -> event = value;
            case "data" -> dataLines.add(value);
            default -> {
                // 忽略 retry、id 等本阶段不需要的 SSE 字段。
            }
        }
        return Optional.empty();
    }

    public Optional<SseEvent> finish() {
        return emitIfPresent();
    }

    public List<SseEvent> parseLines(List<String> lines) {
        List<SseEvent> events = new ArrayList<>();
        for (String line : lines) {
            accept(line).ifPresent(events::add);
        }
        finish().ifPresent(events::add);
        return List.copyOf(events);
    }

    private Optional<SseEvent> emitIfPresent() {
        if (event == null && dataLines.isEmpty()) {
            return Optional.empty();
        }
        SseEvent sseEvent = new SseEvent(event == null ? "message" : event, String.join("\n", dataLines));
        event = null;
        dataLines.clear();
        return Optional.of(sseEvent);
    }

    private String stripSingleLeadingSpace(String value) {
        if (value.startsWith(" ")) {
            return value.substring(1);
        }
        return value;
    }
}