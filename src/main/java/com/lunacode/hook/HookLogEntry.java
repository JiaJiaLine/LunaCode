package com.lunacode.hook;

import java.time.Instant;
import java.util.Map;

public record HookLogEntry(
        Instant timestamp,
        String hookId,
        String eventName,
        String actionType,
        String status,
        long durationMillis,
        String output,
        String error,
        Map<String, Object> metadata
) {
    public HookLogEntry {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        hookId = hookId == null ? "" : hookId;
        eventName = eventName == null ? "" : eventName;
        actionType = actionType == null ? "" : actionType;
        status = status == null ? "" : status;
        output = output == null ? "" : output;
        error = error == null ? "" : error;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
