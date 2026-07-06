package com.lunacode.background;

import com.lunacode.subagent.SubAgentRunHandle;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultForegroundSubAgentTracker implements ForegroundSubAgentTracker {
    private final BackgroundTaskManager backgroundTaskManager;
    private final AtomicReference<Entry> current = new AtomicReference<>();

    public DefaultForegroundSubAgentTracker(BackgroundTaskManager backgroundTaskManager) {
        this.backgroundTaskManager = Objects.requireNonNull(backgroundTaskManager, "backgroundTaskManager");
    }

    @Override
    public void setCurrent(SubAgentRunHandle handle, String task) {
        if (handle != null) {
            current.set(new Entry(handle, task == null ? "" : task));
        }
    }

    @Override
    public Optional<SubAgentRunHandle> current() {
        Entry entry = current.get();
        return entry == null ? Optional.empty() : Optional.of(entry.handle());
    }

    @Override
    public Optional<String> adoptCurrentToBackground() {
        Entry entry = current.getAndSet(null);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(backgroundTaskManager.adoptRunning(entry.handle(), entry.task()));
    }

    @Override
    public void clear(SubAgentRunHandle handle) {
        Entry entry = current.get();
        if (entry != null && entry.handle() == handle) {
            current.compareAndSet(entry, null);
        }
    }

    private record Entry(SubAgentRunHandle handle, String task) {}
}
