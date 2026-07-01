package com.lunacode.context;

import com.lunacode.conversation.TokenUsage;
import com.lunacode.tool.ToolExecutionRecord;

import java.util.List;

final class NoOpContextManager implements ContextManager {
    static final NoOpContextManager INSTANCE = new NoOpContextManager();

    private NoOpContextManager() {
    }

    @Override
    public ContextPreparationResult prepareBeforeTurn(ContextPreparationRequest request) {
        return ContextPreparationResult.proceed(request == null ? CompactTrigger.AUTO_CHECK : request.trigger());
    }

    @Override
    public ContextPreparationResult compactManually(ContextPreparationRequest request) {
        return new ContextPreparationResult(true, false, CompactTrigger.MANUAL, 0, 0, 0, 0, 0, "当前上下文管理未启用。");
    }

    @Override
    public void recordToolExecutions(List<ToolExecutionRecord> records) {
    }

    @Override
    public void recordProviderUsage(TokenUsage usage) {
    }
}
