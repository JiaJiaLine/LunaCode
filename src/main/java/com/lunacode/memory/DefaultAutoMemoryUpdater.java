package com.lunacode.memory;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class DefaultAutoMemoryUpdater implements AutoMemoryUpdater {
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|token|authorization|password|secret|private[_-]?key)\\s*[:=]\\s*\\S+"
    );

    private final MemoryModelClient modelClient;
    private final MemoryStore store;
    private final MemoryRuntimeState runtimeState;
    private final ExecutorService executor;
    private final Runnable onStateChanged;

    public DefaultAutoMemoryUpdater(MemoryModelClient modelClient, MemoryStore store, MemoryRuntimeState runtimeState) {
        this(modelClient, store, runtimeState, () -> {});
    }

    public DefaultAutoMemoryUpdater(MemoryModelClient modelClient, MemoryStore store, MemoryRuntimeState runtimeState, Runnable onStateChanged) {
        this(modelClient, store, runtimeState, onStateChanged, Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "lunacode-memory");
            thread.setDaemon(true);
            return thread;
        }));
    }

    DefaultAutoMemoryUpdater(MemoryModelClient modelClient, MemoryStore store, MemoryRuntimeState runtimeState, ExecutorService executor) {
        this(modelClient, store, runtimeState, () -> {}, executor);
    }

    DefaultAutoMemoryUpdater(MemoryModelClient modelClient, MemoryStore store, MemoryRuntimeState runtimeState, Runnable onStateChanged, ExecutorService executor) {
        this.modelClient = modelClient;
        this.store = store;
        this.runtimeState = runtimeState;
        this.executor = executor;
        this.onStateChanged = onStateChanged == null ? () -> {} : onStateChanged;
    }

    @Override
    public void updateAsync(MemoryUpdateRequest request) {
        if (runtimeState == null || !runtimeState.autoUpdateEnabled()) {
            setLatestState("off");
            return;
        }
        setLatestState("running");
        executor.submit(() -> update(request));
    }

    private void update(MemoryUpdateRequest request) {
        try {
            List<MemoryUpdateAction> actions = modelClient.proposeUpdates(request);
            boolean changed = false;
            for (MemoryUpdateAction action : actions) {
                if (action.kind() == MemoryUpdateAction.ActionKind.NOOP) {
                    continue;
                }
                if (action.kind() == MemoryUpdateAction.ActionKind.DELETE) {
                    changed |= store.delete(action.targetId().orElse(""));
                    continue;
                }
                if (containsSensitive(action.body().orElse("")) || containsSensitive(action.title().orElse(""))) {
                    continue;
                }
                store.upsert(action, request.sessionId());
                changed = true;
            }
            if (changed) {
                store.rebuildIndexes();
                setLatestState("updated");
            } else {
                setLatestState("noop");
            }
        } catch (Exception e) {
            setLatestState("failed: " + summarize(e.getMessage()));
        }
    }

    private void setLatestState(String state) {
        if (runtimeState != null) {
            runtimeState.setLatestState(state);
        }
        onStateChanged.run();
    }

    private boolean containsSensitive(String value) {
        return value != null && SENSITIVE_PATTERN.matcher(value).find();
    }

    private String summarize(String value) {
        String normalized = value == null ? "未知原因" : value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120).toLowerCase(Locale.ROOT);
    }
}
