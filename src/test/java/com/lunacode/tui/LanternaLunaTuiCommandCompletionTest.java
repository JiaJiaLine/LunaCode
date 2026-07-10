package com.lunacode.tui;

import com.lunacode.command.SlashCommandCompletion;
import com.lunacode.command.SlashCommandName;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.orchestrator.ChatOrchestrator;
import com.lunacode.orchestrator.StatusSnapshot;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanternaLunaTuiCommandCompletionTest {
    private static final TerminalProfile TEST_PROFILE =
            new TerminalProfile(true, false, false, 0);

    @Test
    void tabSingleMatchReplacesCommandTokenAndKeepsArguments() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(
                new SlashCommandCompletion.Single("/permission")
        );
        Harness harness = harness(orchestrator, 80);
        harness.insert("/pe args");

        harness.handleKey('\t');

        assertEquals("/permission args", harness.input().content());
        assertEquals("/permission".length(), harness.input().cursorIndex());
        assertFalse(harness.completionMenuVisible());
        assertTrue(harness.output().contains("> /permission args"));
    }

    @Test
    void tabMultipleMatchesRendersCandidatesAndAliasAttributionInLiveRegion() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Multiple(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/perm", "/permission"),
                new SlashCommandName("/permission", "/permission")
        )));
        Harness harness = harness(orchestrator, 80);
        harness.insert("/p");

        harness.handleKey('\t');

        assertTrue(harness.completionMenuVisible());
        assertTrue(harness.output().contains("候选: /plan"));
        assertTrue(harness.output().contains("/perm -> /permission"));
        assertTrue(harness.output().contains("/permission"));
        assertTrue(harness.output().contains("> /p"));
    }

    @Test
    void narrowTerminalWrapsCandidatesAcrossLiveRegionLines() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Multiple(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/permission", "/permission"),
                new SlashCommandName("/status", "/status")
        )));
        Harness harness = harness(orchestrator, 14);
        harness.insert("/p");

        harness.handleKey('\t');

        String output = harness.output();
        assertTrue(output.contains("候选: /plan"));
        assertTrue(output.contains(System.lineSeparator() + "  /permission"));
        assertTrue(output.contains(System.lineSeparator() + "  /status"));
        assertTrue(harness.completionMenuVisible());
    }

    @Test
    void typingAfterMultipleMatchesRemovesCandidatesButPreservesEditedInput() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Multiple(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/permission", "/permission")
        )));
        Harness harness = harness(orchestrator, 80);
        harness.insert("/p");
        harness.handleKey('\t');
        harness.clearOutput();

        harness.handleKey('x');

        assertFalse(harness.completionMenuVisible());
        assertEquals("/px", harness.input().content());
        assertFalse(harness.output().contains("候选:"));
        assertTrue(harness.output().contains("> /px"));
    }

    @Test
    void animationTickRedrawKeepsCandidatesAndInputInSameLiveRegion() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(
                new SlashCommandCompletion.Multiple(List.of(
                        new SlashCommandName("/plan", "/plan"),
                        new SlashCommandName("/permission", "/permission")
                )),
                respondingStatus()
        );
        Harness harness = harness(orchestrator, 80);
        harness.insert("/p");
        harness.handleKey('\t');

        assertTrue(harness.ticker.running());
        harness.clearOutput();
        harness.clock.advance(Duration.ofMillis(100));

        harness.ticker.fire();

        assertTrue(harness.completionMenuVisible());
        assertEquals("/p", harness.input().content());
        assertTrue(harness.output().contains("候选: /plan  /permission"));
        assertTrue(harness.output().contains("> /p"));
    }

    @Test
    void clearVisibleScreenClearsInputCandidatesAndLiveRegionState() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Multiple(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/permission", "/permission")
        )));
        Harness harness = harness(orchestrator, 80);
        harness.insert("/p");
        harness.handleKey('\t');
        harness.clearOutput();

        harness.tui.clearVisibleScreen();

        assertEquals("", harness.input().content());
        assertFalse(harness.completionMenuVisible());
        assertFalse(harness.ticker.running());
        assertTrue(harness.output().contains("\u001B[2J\u001B[H"));
        assertTrue(harness.output().contains("LunaCode test"));
        assertFalse(harness.output().contains("候选:"));
        assertTrue(harness.output().contains("> "));
    }

    private Harness harness(FakeOrchestrator orchestrator, int width) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T10:00:00Z"));
        ManualTicker ticker = new ManualTicker();
        LanternaLunaTui tui = new LanternaLunaTui(
                new DefaultConversationManager(),
                orchestrator,
                clock,
                ticker,
                TEST_PROFILE,
                new LunaTheme(),
                new TerminalLayout(),
                new LiveRegionRenderer(),
                new Spinner(),
                new TuiActivityTracker(),
                new BuildInfo("test")
        );
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        Terminal terminal = terminal(writer, width);
        Field terminalField = LanternaLunaTui.class.getDeclaredField("terminal");
        terminalField.setAccessible(true);
        terminalField.set(tui, terminal);
        return new Harness(tui, output, ticker, clock);
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

    private static StatusSnapshot respondingStatus() {
        return new StatusSnapshot("test", "model", null, null, "responding", null);
    }

    private static class Harness {
        private final LanternaLunaTui tui;
        private final StringWriter output;
        private final ManualTicker ticker;
        private final MutableClock clock;

        private Harness(
                LanternaLunaTui tui,
                StringWriter output,
                ManualTicker ticker,
                MutableClock clock
        ) {
            this.tui = tui;
            this.output = output;
            this.ticker = ticker;
            this.clock = clock;
        }

        private void insert(String value) throws Exception {
            for (char c : value.toCharArray()) {
                input().insert(c);
            }
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

        private boolean completionMenuVisible() throws Exception {
            Field field = LanternaLunaTui.class.getDeclaredField("completionMenuVisible");
            field.setAccessible(true);
            return (boolean) field.get(tui);
        }

        private String output() {
            return output.toString();
        }

        private void clearOutput() {
            output.getBuffer().setLength(0);
        }
    }

    private static class FakeOrchestrator implements ChatOrchestrator {
        private final SlashCommandCompletion completion;
        private final StatusSnapshot status;

        private FakeOrchestrator(SlashCommandCompletion completion) {
            this(completion, StatusSnapshot.idle("test", "model"));
        }

        private FakeOrchestrator(SlashCommandCompletion completion, StatusSnapshot status) {
            this.completion = completion;
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
            return completion;
        }

        @Override
        public StatusSnapshot status() {
            return status;
        }
    }

    private static class ManualTicker implements AnimationTicker {
        private Runnable tick;
        private boolean running;

        @Override
        public void start(Runnable tick) {
            this.tick = tick;
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean running() {
            return running;
        }

        private void fire() {
            if (running && tick != null) {
                tick.run();
            }
        }

        @Override
        public void close() {
            stop();
            tick = null;
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
