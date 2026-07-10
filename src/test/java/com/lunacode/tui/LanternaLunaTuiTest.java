package com.lunacode.tui;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.command.SlashCommandCompletion;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.InternalMessage;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.orchestrator.ChatOrchestrator;
import com.lunacode.orchestrator.StatusSnapshot;
import com.lunacode.tool.ToolResult;
import org.jline.terminal.Terminal;
import org.jline.terminal.Attributes;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanternaLunaTuiTest {
    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");

    @Test
    void printsCompactFourLineBrandCard() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("anthropic", "claude-sonnet"));

        harness.printBrandCard(StatusSnapshot.idle("anthropic", "claude-sonnet"));

        String output = harness.output();
        assertEquals(4, output.lines().count());
        assertTrue(output.contains("(L) LunaCode 9.9.9"));
        assertTrue(output.contains("模型  claude-sonnet"));
        assertTrue(output.contains("目录  "));
        assertTrue(output.contains("/help 查看命令 | Esc 退出/取消"));
        assertFalse(output.contains("\u001B["));
    }

    @Test
    void narrowBrandCardKeepsCoreFieldsWithinTerminalWidth() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("anthropic", "very-long-model-name"), 20);

        harness.printBrandCard(StatusSnapshot.idle("anthropic", "very-long-model-name"));

        List<String> lines = harness.output().lines().toList();
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).contains("LunaCode"));
        assertTrue(lines.get(1).startsWith("模型"));
        assertTrue(lines.get(2).startsWith("目录"));
        assertTrue(lines.get(3).contains("/help"));
        TerminalLayout layout = new TerminalLayout();
        assertTrue(lines.stream().allMatch(line -> layout.columns(line) <= 20));
    }

    @Test
    void appendsUserAndAssistantMessagesIncrementallyWithoutInternalLabels() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("test", "model-x"));
        InternalMessage user = message("u1", MessageRole.USER, MessageStatus.COMPLETE, "请读取文件");
        InternalMessage firstChunk = message("a1", MessageRole.ASSISTANT, MessageStatus.STREAMING, "正在");
        InternalMessage secondChunk = message("a1", MessageRole.ASSISTANT, MessageStatus.STREAMING, "正在读取");
        InternalMessage complete = message("a1", MessageRole.ASSISTANT, MessageStatus.COMPLETE, "正在读取");

        harness.tui().render(List.of(user, firstChunk), StatusSnapshot.idle("test", "model-x"));
        harness.tui().render(List.of(user, secondChunk), StatusSnapshot.idle("test", "model-x"));
        harness.tui().render(List.of(user, complete), StatusSnapshot.idle("test", "model-x"));

        String output = harness.output();
        assertTrue(output.contains("> 请读取文件" + System.lineSeparator()));
        assertTrue(output.contains("* 正在读取" + System.lineSeparator()));
        assertEquals(1, occurrences(output, "> 请读取文件"));
        assertEquals(1, occurrences(output, "* 正在读取"));
        assertFalse(output.contains("[complete]"));
        assertFalse(output.contains("[streaming]"));
    }

    @Test
    void fixedClockStartsTickerForModelAndStopsAfterFirstStreamEvent() throws Exception {
        StatusSnapshot responding = new StatusSnapshot(
                "test", "model-x", null, null, "responding", null
        );
        Harness harness = harness(responding);

        harness.tui().render(List.of(), responding);

        assertTrue(harness.ticker().running());
        assertEquals(1, harness.ticker().startCalls());
        assertTrue(harness.output().contains("Luna 正在思考"));
        assertTrue(harness.output().contains("0.0s"));
        assertTrue(harness.output().contains("Esc 取消"));

        harness.tracker().onAgentEvent(new AgentEvent.StreamText("首字"), NOW);
        InternalMessage streaming = message(
                "a-stream", MessageRole.ASSISTANT, MessageStatus.STREAMING, "首字"
        );
        harness.tui().render(List.of(streaming), responding);

        assertFalse(harness.ticker().running());
        assertTrue(harness.ticker().stopCalls() >= 1);
    }

    @Test
    void printsBlockingAndFinalStatusesOnlyOnce() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("test", "model"));
        StatusSnapshot permission = new StatusSnapshot(
                "test", "model", null, null,
                "waiting_permission", "确认写入 src/Main.java", "WriteFile", "确认写入 src/Main.java"
        ).withSessionAndMemory("sess-1", true, "updated");
        StatusSnapshot error = new StatusSnapshot(
                "test", "model", null, null, "error", "模型不可用"
        );

        harness.printStatus(permission);
        harness.printStatus(permission);
        harness.printStatus(error);
        harness.printStatus(error);

        String output = harness.output();
        assertEquals(1, occurrences(output, "权限确认 (default)"));
        assertEquals(1, occurrences(output, "错误 | 模型不可用"));
        assertTrue(output.contains("s:sess-1"));
        assertFalse(output.contains("waiting_permission"));
    }

    @Test
    void printsOneCompactToolCompletionWithoutFullResult() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("test", "model"));
        harness.tracker().onAgentEvent(
                new AgentEvent.ToolUseStarted(
                        "call-1",
                        "Bash",
                        JsonNodeFactory.instance.objectNode().put("command", "mvn test")
                ),
                NOW.minusSeconds(2)
        );
        harness.tracker().onAgentEvent(
                new AgentEvent.ToolResultReady(
                        "call-1",
                        "Bash",
                        ToolResult.success("very-secret-full-output", Map.of()),
                        Duration.ofSeconds(2)
                ),
                NOW
        );

        harness.tui().render(List.of(), StatusSnapshot.idle("test", "model"));
        harness.tui().render(List.of(), StatusSnapshot.idle("test", "model"));

        String output = harness.output();
        assertTrue(output.contains("+ Bash | mvn test | 2s"));
        assertEquals(1, occurrences(output, "Bash | mvn test"));
        assertFalse(output.contains("very-secret-full-output"));
    }

    @Test
    void failedToolRecordPrioritizesErrorAndKeepsDuration() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("test", "model"));
        String longCommand = "command-" + "x".repeat(300);
        harness.tracker().onAgentEvent(new AgentEvent.ToolUseStarted(
                "failed-call",
                "Bash",
                JsonNodeFactory.instance.objectNode().put("command", longCommand)
        ), NOW.minusSeconds(3));
        harness.tracker().onAgentEvent(new AgentEvent.ToolResultReady(
                "failed-call",
                "Bash",
                ToolResult.error("permission denied", Map.of()),
                Duration.ofSeconds(3)
        ), NOW);

        harness.tui().render(List.of(), StatusSnapshot.idle("test", "model"));

        String output = harness.output();
        assertTrue(output.contains("x Bash | permission denied"));
        assertTrue(output.contains("| 3s"));
        assertFalse(output.contains(longCommand));
    }

    @Test
    void clearScreenResetsHistoryCachesAndRedrawsBrandAndPrompt() throws Exception {
        Harness harness = harness(StatusSnapshot.idle("test", "model"));
        harness.conversation().addMessage(MessageRole.USER, "旧消息不应重绘");
        harness.tui().requestRender();
        harness.resetOutput();

        harness.tui().clearVisibleScreen();

        String cleared = harness.output();
        assertTrue(cleared.contains("\u001B[2J\u001B[H"));
        assertTrue(cleared.contains("(L) LunaCode 9.9.9"));
        assertTrue(cleared.contains("model | mode:Agent | perm:default"));
        assertTrue(cleared.contains("> "));
        assertFalse(cleared.contains("旧消息不应重绘"));

        harness.conversation().addMessage(MessageRole.USER, "清屏后的新消息");
        harness.tui().requestRender();

        assertTrue(harness.output().contains("> 清屏后的新消息"));
    }

    @Test
    void streamingErrorEndsAssistantLineAndPrintsSummaryOnlyOnce() throws Exception {
        StatusSnapshot responding = new StatusSnapshot("test", "model", null, null, "responding", null);
        StatusSnapshot error = new StatusSnapshot("test", "model", null, null, "error", "provider failed");
        Harness harness = harness(responding);
        InternalMessage streaming = message(
                "a-error", MessageRole.ASSISTANT, MessageStatus.STREAMING, "partial"
        );

        harness.tui().render(List.of(streaming), responding);
        harness.tui().render(List.of(streaming), error);
        assertFalse(harness.output().contains("provider failed"));

        InternalMessage failed = new InternalMessage(
                "a-error",
                MessageRole.ASSISTANT,
                MessageStatus.ERROR,
                NOW,
                null,
                "partial",
                "provider failed"
        );
        harness.tui().render(List.of(failed), error);

        String output = harness.output();
        assertTrue(output.contains("partial" + System.lineSeparator() + "x provider failed"));
        assertEquals(1, occurrences(output, "provider failed"));
    }

    @Test
    void longToolTargetKeepsElapsedAndCancelHint() throws Exception {
        StatusSnapshot toolRunning = new StatusSnapshot(
                "test", "model", null, null,
                "tool_running", null, "Bash", "running"
        );
        Harness harness = harness(toolRunning);
        String longCommand = "echo-" + "x".repeat(300);
        harness.tracker().onAgentEvent(new AgentEvent.ToolUseStarted(
                "long-tool",
                "Bash",
                JsonNodeFactory.instance.objectNode().put("command", longCommand)
        ), NOW);

        harness.tui().render(List.of(), toolRunning);

        assertTrue(harness.output().contains("0.0s | Esc 取消"));
        assertFalse(harness.output().contains(longCommand));
    }

    @Test
    void clearDuringStreamRestoresAssistantMarkerForLaterDelta() throws Exception {
        StatusSnapshot responding = new StatusSnapshot("test", "model", null, null, "responding", null);
        Harness harness = harness(responding);
        harness.conversation().addStreamingAssistantMessage();
        String id = harness.conversation().snapshot().get(0).id();
        harness.conversation().appendContent(id, "旧内容");
        harness.tui().requestRender();

        harness.tui().clearVisibleScreen();
        harness.resetOutput();
        harness.conversation().appendContent(id, "继续");
        harness.tui().requestRender();

        assertTrue(harness.output().contains("* 继续"));
        assertFalse(harness.output().contains("旧内容"));
    }

    @Test
    void typingAndMovingCursorDuringStreamPreservesMixedInput() throws Exception {
        StatusSnapshot responding = new StatusSnapshot("test", "model", null, null, "responding", null);
        Harness harness = harness(responding);
        String id = harness.conversation().addStreamingAssistantMessage();
        harness.conversation().appendContent(id, "回复中");
        harness.tui().requestRender();

        harness.handleKey('下');
        harness.input().insert(0x1F642);
        harness.handleKey('一');
        harness.input().moveLeft();

        assertEquals("下🙂一", harness.input().content());
        int cursorBeforeCompletion = harness.input().cursorIndex();
        harness.conversation().completeMessage(id, TokenUsage.unknown());
        harness.tui().requestRender();

        assertEquals("下🙂一", harness.input().content());
        assertEquals(cursorBeforeCompletion, harness.input().cursorIndex());
        assertTrue(harness.output().contains("> 下🙂一"));
    }

    @Test
    void terminalAttributesAreRestoredEvenWhenFlushFails() throws Exception {
        FakeTicker ticker = new FakeTicker();
        LanternaLunaTui tui = new LanternaLunaTui(
                new DefaultConversationManager(),
                new FakeOrchestrator(StatusSnapshot.idle("test", "model")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                ticker,
                new TerminalProfile(true, false, false, 0),
                new LunaTheme(),
                new TerminalLayout(),
                new LiveRegionRenderer(),
                new Spinner(),
                new TuiActivityTracker(),
                new BuildInfo("test")
        );
        AtomicBoolean attributesRestored = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        PrintWriter writer = new PrintWriter(new StringWriter(), true);
        Terminal failingTerminal = (Terminal) Proxy.newProxyInstance(
                Terminal.class.getClassLoader(),
                new Class<?>[]{Terminal.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "writer" -> writer;
                    case "flush" -> throw new IllegalStateException("flush failed");
                    case "setAttributes" -> {
                        attributesRestored.set(true);
                        yield null;
                    }
                    case "close" -> {
                        closed.set(true);
                        yield null;
                    }
                    case "getWidth" -> 80;
                    case "getType" -> "xterm-256color";
                    default -> defaultValue(method.getReturnType());
                }
        );
        setField(tui, "terminal", failingTerminal);
        setField(tui, "originalAttributes", new Attributes());

        Method restore = LanternaLunaTui.class.getDeclaredMethod("restoreTerminal");
        restore.setAccessible(true);
        restore.invoke(tui);

        assertTrue(attributesRestored.get());
        assertTrue(closed.get());
    }

    @Test
    void closingTuiCancelsAgentEventSubscription() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(StatusSnapshot.idle("test", "model"));
        LanternaLunaTui tui = new LanternaLunaTui(
                new DefaultConversationManager(),
                orchestrator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new FakeTicker(),
                new TerminalProfile(true, false, false, 0),
                new LunaTheme(),
                new TerminalLayout(),
                new LiveRegionRenderer(),
                new Spinner(),
                new TuiActivityTracker(),
                new BuildInfo("test")
        );
        Method subscribe = LanternaLunaTui.class.getDeclaredMethod("subscribeToAgentEvents");
        subscribe.setAccessible(true);
        subscribe.invoke(tui);

        Method restore = LanternaLunaTui.class.getDeclaredMethod("restoreTerminal");
        restore.setAccessible(true);
        restore.invoke(tui);

        assertTrue(orchestrator.subscribed.get());
        assertTrue(orchestrator.subscriptionClosed.get());
    }

    private Harness harness(StatusSnapshot initialStatus) throws Exception {
        return harness(initialStatus, 100);
    }

    private Harness harness(StatusSnapshot initialStatus, int width) throws Exception {
        DefaultConversationManager conversation = new DefaultConversationManager();
        FakeOrchestrator orchestrator = new FakeOrchestrator(initialStatus);
        FakeTicker ticker = new FakeTicker();
        TuiActivityTracker tracker = new TuiActivityTracker();
        LanternaLunaTui tui = new LanternaLunaTui(
                conversation,
                orchestrator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ticker,
                new TerminalProfile(true, false, false, 0),
                new LunaTheme(),
                new TerminalLayout(),
                new LiveRegionRenderer(),
                new Spinner(),
                tracker,
                new BuildInfo("9.9.9")
        );
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        Terminal terminal = terminal(writer, width);
        Field terminalField = LanternaLunaTui.class.getDeclaredField("terminal");
        terminalField.setAccessible(true);
        terminalField.set(tui, terminal);
        return new Harness(tui, conversation, ticker, tracker, output);
    }

    private Terminal terminal(PrintWriter writer, int width) {
        return (Terminal) Proxy.newProxyInstance(
                Terminal.class.getClassLoader(),
                new Class<?>[]{Terminal.class},
                (proxy, method, args) -> {
                    if ("writer".equals(method.getName())) {
                        return writer;
                    }
                    if ("flush".equals(method.getName())) {
                        writer.flush();
                        return null;
                    }
                    if ("getWidth".equals(method.getName())) {
                        return width;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                }
        );
    }

    private InternalMessage message(
            String id,
            MessageRole role,
            MessageStatus status,
            String content
    ) {
        return new InternalMessage(id, role, status, NOW, null, content, null);
    }

    private int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Harness(
            LanternaLunaTui tui,
            DefaultConversationManager conversation,
            FakeTicker ticker,
            TuiActivityTracker tracker,
            StringWriter outputWriter
    ) {
        private String output() {
            return outputWriter.toString();
        }

        private void resetOutput() {
            outputWriter.getBuffer().setLength(0);
        }

        private void printBrandCard(StatusSnapshot status) throws Exception {
            Method method = LanternaLunaTui.class.getDeclaredMethod("printBrandCard", StatusSnapshot.class);
            method.setAccessible(true);
            method.invoke(tui, status);
        }

        private void printStatus(StatusSnapshot status) throws Exception {
            Method method = LanternaLunaTui.class.getDeclaredMethod(
                    "printStatus", PrintWriter.class, StatusSnapshot.class
            );
            method.setAccessible(true);
            method.invoke(tui, new PrintWriter(outputWriter, true), status);
        }

        private void handleKey(int key) throws Exception {
            Method method = LanternaLunaTui.class.getDeclaredMethod("handleKey", int.class);
            method.setAccessible(true);
            method.invoke(tui, key);
        }

        private InputLineBuffer input() throws Exception {
            Field field = LanternaLunaTui.class.getDeclaredField("input");
            field.setAccessible(true);
            return (InputLineBuffer) field.get(tui);
        }
    }

    private static final class FakeTicker implements AnimationTicker {
        private Runnable tick;
        private boolean running;
        private int startCalls;
        private int stopCalls;

        @Override
        public void start(Runnable tick) {
            this.tick = tick;
            running = true;
            startCalls++;
        }

        @Override
        public void stop() {
            if (running) {
                stopCalls++;
            }
            running = false;
        }

        @Override
        public boolean running() {
            return running;
        }

        int startCalls() {
            return startCalls;
        }

        int stopCalls() {
            return stopCalls;
        }

        @Override
        public void close() {
            stop();
            tick = null;
        }
    }

    private static final class FakeOrchestrator implements ChatOrchestrator {
        private final StatusSnapshot status;
        private final AtomicBoolean subscribed = new AtomicBoolean();
        private final AtomicBoolean subscriptionClosed = new AtomicBoolean();

        private FakeOrchestrator(StatusSnapshot status) {
            this.status = status;
        }

        @Override
        public void submitUserMessage(String content) {
        }

        @Override
        public void cancelCurrentRun() {
        }

        @Override
        public SlashCommandCompletion completeSlashCommand(String input, int cursorIndex) {
            return new SlashCommandCompletion.NoMatch();
        }

        @Override
        public StatusSnapshot status() {
            return status;
        }

        @Override
        public AutoCloseable observeAgentEvents(com.lunacode.agent.event.AgentEventSink observer) {
            subscribed.set(true);
            return () -> subscriptionClosed.set(true);
        }
    }
}
