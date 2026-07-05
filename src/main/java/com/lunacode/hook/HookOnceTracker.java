package com.lunacode.hook;

public interface HookOnceTracker {
    boolean markIfFirst(String sessionId, String hookId);
}
