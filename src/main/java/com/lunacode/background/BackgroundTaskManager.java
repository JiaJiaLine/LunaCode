package com.lunacode.background;

import com.lunacode.subagent.SubAgentLaunchRequest;
import com.lunacode.subagent.SubAgentRunHandle;

import java.util.List;
import java.util.Optional;

public interface BackgroundTaskManager {
    String launch(SubAgentLaunchRequest request);

    String adoptRunning(SubAgentRunHandle handle, String task);

    Optional<BackgroundTaskSnapshot> get(String taskId);

    List<BackgroundTaskSnapshot> list();

    void addListener(BackgroundTaskListener listener);
}
