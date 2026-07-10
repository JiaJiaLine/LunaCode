package com.lunacode.orchestrator;

import com.lunacode.agent.event.AgentEventSink;
import com.lunacode.command.CommandUiController;
import com.lunacode.command.SlashCommandCompletion;

public interface ChatOrchestrator {
    void submitUserMessage(String content);

    void cancelCurrentRun();

    default void backgroundCurrentSubAgentOrCancel() {
        cancelCurrentRun();
    }

    default SlashCommandCompletion completeSlashCommand(String input, int cursorIndex) {
        return new SlashCommandCompletion.NoMatch();
    }

    default void setCommandUiController(CommandUiController controller) {
    }

    /**
     * 订阅编排器已经处理完成的 Agent 事件。
     *
     * <p>默认实现保持向后兼容：不保存观察者，返回可安全重复关闭的空订阅。</p>
     */
    default AutoCloseable observeAgentEvents(AgentEventSink observer) {
        return () -> {
        };
    }

    StatusSnapshot status();
}
