package com.lunacode.hook;

import com.lunacode.prompt.SystemReminder;
import com.lunacode.prompt.SystemReminderKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryHookReminderStore implements HookReminderStore {
    private final Map<String, List<PendingHookReminder>> reminders = new ConcurrentHashMap<>();

    @Override
    public void add(String sessionId, PendingHookReminder reminder) {
        if (reminder == null) {
            return;
        }
        reminders.computeIfAbsent(key(sessionId), ignored -> new ArrayList<>()).add(reminder);
    }

    @Override
    public List<SystemReminder> drain(String sessionId, int turnIndex) {
        List<PendingHookReminder> current = reminders.get(key(sessionId));
        if (current == null || current.isEmpty()) {
            return List.of();
        }
        List<PendingHookReminder> ready = new ArrayList<>();
        synchronized (current) {
            current.removeIf(reminder -> {
                boolean available = reminder.availableTurnIndex() <= turnIndex;
                if (available) {
                    ready.add(reminder);
                }
                return available;
            });
        }
        ready.sort(Comparator.comparingInt(PendingHookReminder::availableTurnIndex));
        return ready.stream()
                .map(reminder -> new SystemReminder(SystemReminderKind.TEMPORARY_CONSTRAINT, reminder.content(), Math.max(1, turnIndex)))
                .toList();
    }

    private String key(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "unknown-session" : sessionId;
    }
}
