package com.lunacode.hook;

import java.util.LinkedHashMap;
import java.util.Map;

public record HookContext(
        String eventName,
        String toolName,
        Map<String, String> toolArgs,
        String filePath,
        String message,
        String error
) {
    public HookContext {
        eventName = safe(eventName);
        toolName = safe(toolName);
        toolArgs = toolArgs == null ? Map.of() : Map.copyOf(toolArgs);
        filePath = safe(filePath);
        message = safe(message);
        error = safe(error);
    }

    public static HookContext empty(HookEventName event) {
        return new HookContext(event == null ? "" : event.yamlName(), "", Map.of(), "", "", "");
    }

    public HookContext withEvent(HookEventName event) {
        return new HookContext(event == null ? eventName : event.yamlName(), toolName, toolArgs, filePath, message, error);
    }

    public Map<String, String> variables() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventName", eventName);
        values.put("toolName", toolName);
        values.put("tool", toolName);
        values.put("filePath", filePath);
        values.put("message", message);
        values.put("error", error);
        toolArgs.forEach((key, value) -> {
            values.put("toolArgs." + key, value);
            values.put("args." + key, value);
        });
        return values;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
