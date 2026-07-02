package com.lunacode.memory;

public record MemoryConfig(boolean autoUpdate) {
    public static MemoryConfig defaults() {
        return new MemoryConfig(true);
    }
}
