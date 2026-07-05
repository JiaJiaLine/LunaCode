package com.lunacode.hook;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryHookOnceTracker implements HookOnceTracker {
    private final Set<String> executed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markIfFirst(String sessionId, String hookId) {
        return executed.add((sessionId == null ? "" : sessionId) + ":" + (hookId == null ? "" : hookId));
    }
}
