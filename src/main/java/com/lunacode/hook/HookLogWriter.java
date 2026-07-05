package com.lunacode.hook;

public interface HookLogWriter {
    void log(String sessionId, HookLogEntry entry);
}
