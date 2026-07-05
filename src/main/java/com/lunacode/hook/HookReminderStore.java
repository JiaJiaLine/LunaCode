package com.lunacode.hook;

import com.lunacode.prompt.SystemReminder;

import java.util.List;

public interface HookReminderStore {
    void add(String sessionId, PendingHookReminder reminder);

    List<SystemReminder> drain(String sessionId, int turnIndex);
}
