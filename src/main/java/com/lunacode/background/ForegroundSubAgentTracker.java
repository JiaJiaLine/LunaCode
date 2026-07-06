package com.lunacode.background;

import com.lunacode.subagent.SubAgentRunHandle;

import java.util.Optional;

public interface ForegroundSubAgentTracker {
    void setCurrent(SubAgentRunHandle handle, String task);

    Optional<SubAgentRunHandle> current();

    Optional<String> adoptCurrentToBackground();

    void clear(SubAgentRunHandle handle);
}
