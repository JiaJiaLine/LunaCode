package com.lunacode.tui;

import com.lunacode.conversation.InternalMessage;
import com.lunacode.orchestrator.StatusSnapshot;

import java.util.List;

public interface LunaTui {
    void start();

    void render(List<InternalMessage> messages, StatusSnapshot status);

    void showFatalError(String summary);
}