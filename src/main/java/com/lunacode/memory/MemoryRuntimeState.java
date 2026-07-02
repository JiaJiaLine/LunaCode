package com.lunacode.memory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class MemoryRuntimeState {
    private final AtomicBoolean autoUpdateEnabled;
    private final AtomicReference<String> latestState = new AtomicReference<>("idle");

    public MemoryRuntimeState(boolean autoUpdateEnabled) {
        this.autoUpdateEnabled = new AtomicBoolean(autoUpdateEnabled);
    }

    public boolean autoUpdateEnabled() {
        return autoUpdateEnabled.get();
    }

    public void setAutoUpdateEnabled(boolean enabled) {
        autoUpdateEnabled.set(enabled);
    }

    public String latestState() {
        return latestState.get();
    }

    public void setLatestState(String state) {
        latestState.set(state == null || state.isBlank() ? "idle" : state);
    }
}
