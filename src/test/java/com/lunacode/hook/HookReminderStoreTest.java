package com.lunacode.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookReminderStoreTest {
    @Test
    void drainsOnlyAvailableRemindersOnce() {
        InMemoryHookReminderStore store = new InMemoryHookReminderStore();
        store.add("s1", new PendingHookReminder("h1", "now", 1));
        store.add("s1", new PendingHookReminder("h2", "later", 2));

        assertEquals(1, store.drain("s1", 1).size());
        assertEquals(0, store.drain("s1", 1).size());
        assertEquals(1, store.drain("s1", 2).size());
    }
}
