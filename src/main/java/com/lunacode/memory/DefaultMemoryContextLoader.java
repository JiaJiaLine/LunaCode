package com.lunacode.memory;

public final class DefaultMemoryContextLoader implements MemoryContextLoader {
    private final MemoryStore store;

    public DefaultMemoryContextLoader(MemoryStore store) {
        this.store = store;
    }

    @Override
    public MemoryIndexSnapshot loadForPrompt() {
        if (store == null) {
            return MemoryIndexSnapshot.empty();
        }
        return store.loadIndexes();
    }
}
