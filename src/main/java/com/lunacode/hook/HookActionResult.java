package com.lunacode.hook;

import java.util.LinkedHashMap;
import java.util.Map;

public record HookActionResult(boolean success, String output, Map<String, Object> metadata) {
    public HookActionResult {
        output = output == null ? "" : output;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static HookActionResult success(String output) {
        return new HookActionResult(true, output, Map.of());
    }

    public static HookActionResult success(String output, Map<String, Object> metadata) {
        return new HookActionResult(true, output, metadata);
    }

    public static HookActionResult failure(String output, Throwable cause) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (cause != null) {
            metadata.put("error", cause.getClass().getSimpleName());
            metadata.put("message", cause.getMessage());
        }
        return new HookActionResult(false, output, metadata);
    }

    public static HookActionResult failure(String output, Map<String, Object> metadata) {
        return new HookActionResult(false, output, metadata);
    }
}
