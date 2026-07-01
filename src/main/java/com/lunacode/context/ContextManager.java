package com.lunacode.context;

import com.lunacode.conversation.TokenUsage;
import com.lunacode.tool.ToolExecutionRecord;

import java.util.List;

public interface ContextManager {
    ContextPreparationResult prepareBeforeTurn(ContextPreparationRequest request);

    ContextPreparationResult compactManually(ContextPreparationRequest request);

    void recordToolExecutions(List<ToolExecutionRecord> records);

    void recordProviderUsage(TokenUsage usage);

    static ContextManager noop() {
        return NoOpContextManager.INSTANCE;
    }
}
