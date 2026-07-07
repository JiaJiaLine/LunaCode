package com.lunacode.worktree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record WorktreeState(Map<String, WorktreeRecord> active) {
    public WorktreeState {
        Map<String, WorktreeRecord> copy = new LinkedHashMap<>();
        if (active != null) {
            active.forEach((name, record) -> copy.put(requireName(name), Objects.requireNonNull(record, "record")));
        }
        active = Collections.unmodifiableMap(copy);
    }

    public static WorktreeState empty() {
        return new WorktreeState(Map.of());
    }

    public WorktreeState withRecord(WorktreeRecord record) {
        Objects.requireNonNull(record, "record");
        Map<String, WorktreeRecord> copy = new LinkedHashMap<>(active);
        copy.put(record.name(), record);
        return new WorktreeState(copy);
    }

    public WorktreeState without(String name) {
        Map<String, WorktreeRecord> copy = new LinkedHashMap<>(active);
        copy.remove(requireName(name));
        return new WorktreeState(copy);
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }
}
