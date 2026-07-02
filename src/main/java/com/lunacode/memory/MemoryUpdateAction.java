package com.lunacode.memory;

import java.util.Optional;

public record MemoryUpdateAction(
        ActionKind kind,
        MemoryType type,
        Optional<String> targetId,
        Optional<String> title,
        Optional<String> body
) {
    public enum ActionKind {
        ADD,
        UPDATE,
        DELETE,
        NOOP
    }

    public MemoryUpdateAction {
        kind = kind == null ? ActionKind.NOOP : kind;
        type = type == null ? MemoryType.PROJECT_KNOWLEDGE : type;
        targetId = targetId == null ? Optional.empty() : targetId;
        title = title == null ? Optional.empty() : title;
        body = body == null ? Optional.empty() : body;
    }
}
