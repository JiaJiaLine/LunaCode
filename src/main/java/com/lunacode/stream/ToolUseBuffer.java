package com.lunacode.stream;

final class ToolUseBuffer {
    private final String id;
    private final String name;
    private final StringBuilder partialJson = new StringBuilder();

    ToolUseBuffer(String id, String name) {
        this.id = id;
        this.name = name;
    }

    String id() {
        return id;
    }

    String name() {
        return name;
    }

    void append(String value) {
        if (value != null) {
            partialJson.append(value);
        }
    }

    String json() {
        return partialJson.toString();
    }
}
