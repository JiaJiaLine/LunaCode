package com.lunacode.context;

import com.lunacode.config.ContextConfig;

public final class CompactionState {
    private int consecutiveAutoFailures;
    private boolean autoCompactionFused;

    public synchronized void recordSuccess() {
        consecutiveAutoFailures = 0;
        autoCompactionFused = false;
    }

    public synchronized void recordAutoFailure(ContextConfig config) {
        consecutiveAutoFailures++;
        if (consecutiveAutoFailures >= config.maxAutoSummaryFailures()) {
            autoCompactionFused = true;
        }
    }

    public synchronized int consecutiveAutoFailures() {
        return consecutiveAutoFailures;
    }

    public synchronized boolean autoCompactionFused() {
        return autoCompactionFused;
    }
}
