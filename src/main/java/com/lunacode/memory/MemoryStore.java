package com.lunacode.memory;

import java.util.List;
import java.util.Optional;

public interface MemoryStore {
    List<MemoryNote> listAll();

    Optional<MemoryNote> find(String id);

    MemoryNote upsert(MemoryUpdateAction action, String sourceSession);

    boolean delete(String id);

    MemoryIndexSnapshot rebuildIndexes();

    MemoryIndexSnapshot loadIndexes();
}
