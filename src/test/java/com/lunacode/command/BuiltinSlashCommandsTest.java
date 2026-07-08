package com.lunacode.command;

import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.skill.SkillInvocationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinSlashCommandsTest {
    @Test
    void registersAllBuiltinCommandsAndAliases() {
        SlashCommandRegistry registry = registry();

        for (String name : List.of(
                "/help", "/h", "/?",
                "/compact", "/cp",
                "/clear", "/cl",
                "/plan", "/pl",
                "/do", "/d",
                "/session", "/sess",
                "/memory", "/mem",
                "/worktree", "/wt",
                "/team", "/tm",
                "/permission", "/perm", "/permissions",
                "/status", "/st",
                "/review", "/r",
                "/cancel", "/x"
        )) {
            assertNotNull(registry.require(name));
        }
        assertEquals(13, registry.visibleCommands().size());
    }

    @Test
    void helpListsVisibleCommandsAndShowsDetailsByAlias() {
        SlashCommandDispatcher dispatcher = dispatcher();
        FakeRuntime runtime = new FakeRuntime();

        dispatcher.dispatch("/help", runtime);

        assertTrue(runtime.info.contains("/review"));
        assertTrue(runtime.info.contains("/r"));
        assertTrue(runtime.info.contains("用法: /review [额外关注]"));

        dispatcher.dispatch("/help r", runtime);

        assertTrue(runtime.info.contains("/review"));
        assertTrue(runtime.info.contains("审查当前 git diff"));
        assertTrue(runtime.info.contains("类型: PROMPT"));
    }

    @Test
    void statusFormatsRuntimeState() {
        SlashCommandDispatcher dispatcher = dispatcher();
        FakeRuntime runtime = new FakeRuntime();
        runtime.status = new CommandRuntimeStatus(
                AgentMode.PLAN,
                PermissionMode.ACCEPT_EDITS,
                "openai",
                "gpt-test",
                10,
                20,
                "waiting_user",
                "20250115-143000-a3f7",
                true,
                "updated"
        );

        dispatcher.dispatch("/status", runtime);

        assertTrue(runtime.info.contains("Agent 模式: [PLAN]"));
        assertTrue(runtime.info.contains("权限模式: acceptEdits"));
        assertTrue(runtime.info.contains("Provider: openai"));
        assertTrue(runtime.info.contains("输入 token: 10"));
        assertTrue(runtime.info.contains("会话: 20250115-143000-a3f7"));
        assertTrue(runtime.info.contains("记忆: on:updated"));
        assertTrue(runtime.info.contains("运行状态: waiting_user"));
    }

    @Test
    void reviewSendsDefaultPromptAndOptionalFocus() {
        SlashCommandDispatcher dispatcher = dispatcher();
        FakeRuntime runtime = new FakeRuntime();

        dispatcher.dispatch("/review", runtime);

        assertTrue(runtime.sentUserMessage.contains("请审查当前 git diff 中的代码变更"));
        assertTrue(runtime.sentUserMessage.contains("1. 逻辑错误"));
        assertTrue(runtime.sentUserMessage.contains("2. 安全问题"));
        assertTrue(runtime.sentUserMessage.contains("3. 性能问题"));
        assertTrue(runtime.sentUserMessage.contains("4. 代码风格"));
        assertFalse(runtime.sentUserMessage.contains("额外关注"));

        dispatcher.dispatch("/review 并重点看异常处理", runtime);

        assertTrue(runtime.sentUserMessage.contains("额外关注：并重点看异常处理"));
    }

    @Test
    void localCommandsCallRuntimeTargets() {
        SlashCommandDispatcher dispatcher = dispatcher();
        FakeRuntime runtime = new FakeRuntime();

        dispatcher.dispatch("/x", runtime);
        dispatcher.dispatch("/cp", runtime);
        dispatcher.dispatch("/cl", runtime);
        dispatcher.dispatch("/pl", runtime);
        dispatcher.dispatch("/d", runtime);
        dispatcher.dispatch("/sess current", runtime);
        dispatcher.dispatch("/mem list", runtime);

        assertEquals(1, runtime.cancelCalls);
        assertEquals(1, runtime.compactCalls);
        assertEquals(1, runtime.clearCalls);
        assertEquals(1, runtime.planCalls);
        assertEquals(1, runtime.defaultCalls);
        assertEquals("/session current", runtime.sessionRawInput);
        assertEquals("/memory list", runtime.memoryRawInput);
    }

    @Test
    void permissionShowsSwitchesAndRequiresDangerousConfirmation() {
        SlashCommandDispatcher dispatcher = dispatcher();
        FakeRuntime runtime = new FakeRuntime();

        dispatcher.dispatch("/permission", runtime);
        assertTrue(runtime.info.contains("default"));

        dispatcher.dispatch("/permissions acceptEdits", runtime);
        assertEquals(PermissionMode.ACCEPT_EDITS, runtime.switchedMode);

        dispatcher.dispatch("/permission bypassPermissions", runtime);
        assertEquals(PermissionMode.BYPASS_PERMISSIONS, runtime.dangerousMode);

        dispatcher.dispatch("/permission nope", runtime);
        assertTrue(runtime.error.contains("未知权限模式"));
    }

    private SlashCommandRegistry registry() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        BuiltinSlashCommands.registerAll(registry);
        return registry;
    }

    private SlashCommandDispatcher dispatcher() {
        return new SlashCommandDispatcher(registry(), new SlashCommandParser());
    }

    private static class FakeRuntime implements CommandRuntime {
        private String info = "";
        private String warning = "";
        private String error = "";
        private String sentUserMessage = "";
        private String sessionRawInput = "";
        private String memoryRawInput = "";
        private int cancelCalls;
        private int compactCalls;
        private int clearCalls;
        private int planCalls;
        private int defaultCalls;
        private PermissionMode switchedMode;
        private PermissionMode dangerousMode;
        private CommandRuntimeStatus status = new CommandRuntimeStatus(AgentMode.DEFAULT, PermissionMode.DEFAULT, "test", "model", null, null, "idle", "", null, "");

        @Override public boolean isBusy() { return false; }
        @Override public boolean hasPendingUserAnswer() { return false; }
        @Override public boolean hasPendingPermissionAnswer() { return false; }
        @Override public boolean hasPendingDangerousModeConfirmation() { return false; }
        @Override public CommandRuntimeStatus runtimeStatus() { return status; }
        @Override public void showInfo(String message) { info = message; }
        @Override public void showWarning(String message) { warning = message; }
        @Override public void showError(String message) { error = message; }
        @Override public void requestRender() {}
        @Override public void cancelCurrentRun() { cancelCalls++; }
        @Override public void clearVisibleScreen() { clearCalls++; }
        @Override public void sendUserMessage(String message) { sentUserMessage = message; }
        @Override public void submitSkillInvocation(SkillInvocationRequest request) {}
        @Override public void compactContext() { compactCalls++; }
        @Override public void enterPlanMode() { planCalls++; }
        @Override public void enterDefaultMode() { defaultCalls++; }
        @Override public void switchPermissionMode(PermissionMode mode) { switchedMode = mode; status = new CommandRuntimeStatus(status.agentMode(), mode, status.provider(), status.model(), status.inputTokens(), status.outputTokens(), status.state(), status.sessionShortId(), status.memoryAutoUpdateEnabled(), status.memoryLatestState()); }
        @Override public void requestDangerousPermissionMode(PermissionMode mode) { dangerousMode = mode; }
        @Override public void runSessionCommand(String rawInput) { sessionRawInput = rawInput; }
        @Override public void runMemoryCommand(String rawInput) { memoryRawInput = rawInput; }
    }
}
