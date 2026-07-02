package com.lunacode.memory;

import java.util.List;

public interface MemoryModelClient {
    List<MemoryUpdateAction> proposeUpdates(MemoryUpdateRequest request);
}
