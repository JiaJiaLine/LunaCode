package com.lunacode.command;

import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlashCommandDispatcherTest {
    @Test
    void returnsNotCommandForNormalInput() {
        SlashCommandDispatcher dispatcher = dispatcher();

        assertEquals(DispatchResult.NOT_COMMAND, dispatcher.dispatch("hello", new FakeRuntime()));
    }

    @Test
    void dispatchesKnownCommandAndKeepsArgs() {
        FakeRuntime runtime = new FakeRuntime();
        SlashCommandDispatcher dispatcher = dispatcher();

        assertEquals(DispatchResult.HANDLED, dispatcher.dispatch("/echo hi", runtime));

        assertEquals("hi", runtime.info);
    }

    @Test
    void unknownCommandShowsHelpHint() {
        FakeRuntime runtime = new FakeRuntime();
        SlashCommandDispatcher dispatcher = dispatcher();

        dispatcher.dispatch("/missing", runtime);

        assertTrue(runtime.error.contains("未知命令"));
        assertTrue(runtime.error.contains("/help"));
    }

    @Test
    void busyRuntimeBlocksNonCancelCommand() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.busy = true;
        SlashCommandDispatcher dispatcher = dispatcher();

        dispatcher.dispatch("/echo hi", runtime);

        assertTrue(runtime.warning.contains("当前忙"));
        assertEquals("", runtime.info);
    }

    @Test
    void cancelRunsEvenWhenBusy() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.busy = true;
        SlashCommandDispatcher dispatcher = dispatcher();

        dispatcher.dispatch("/x", runtime);

        assertEquals(1, runtime.cancelCalls);
    }

    private SlashCommandDispatcher dispatcher() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        registry.register(new SlashCommandDefinition("/echo", List.of(), "回显", "/echo <text>", SlashCommandType.LOCAL, "<text>", false, context -> context.runtime().showInfo(context.args())));
        registry.register(new SlashCommandDefinition("/cancel", List.of("/x"), "取消", "/cancel", SlashCommandType.UI_STATE, "", false, context -> context.runtime().cancelCurrentRun()));
        return new SlashCommandDispatcher(registry, new SlashCommandParser());
    }

    private static class FakeRuntime implements CommandRuntime {
        private boolean busy;
        private String info = "";
        private String warning = "";
        private String error = "";
        private int cancelCalls;

        @Override public boolean isBusy() { return busy; }
        @Override public boolean hasPendingUserAnswer() { return false; }
        @Override public boolean hasPendingPermissionAnswer() { return false; }
        @Override public boolean hasPendingDangerousModeConfirmation() { return false; }
        @Override public CommandRuntimeStatus runtimeStatus() { return new CommandRuntimeStatus(AgentMode.DEFAULT, PermissionMode.DEFAULT, "test", "model", null, null, "idle", "", null, ""); }
        @Override public void showInfo(String message) { info = message; }
        @Override public void showWarning(String message) { warning = message; }
        @Override public void showError(String message) { error = message; }
        @Override public void requestRender() {}
        @Override public void cancelCurrentRun() { cancelCalls++; }
        @Override public void clearVisibleScreen() {}
        @Override public void sendUserMessage(String message) {}
        @Override public void compactContext() {}
        @Override public void enterPlanMode() {}
        @Override public void enterDefaultMode() {}
        @Override public void switchPermissionMode(PermissionMode mode) {}
        @Override public void requestDangerousPermissionMode(PermissionMode mode) {}
        @Override public void runSessionCommand(String rawInput) {}
        @Override public void runMemoryCommand(String rawInput) {}
    }
}
