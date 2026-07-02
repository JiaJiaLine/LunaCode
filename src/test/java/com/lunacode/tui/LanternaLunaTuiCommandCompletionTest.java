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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanternaLunaTuiCommandCompletionTest {
    @Test
    void tabSingleMatchReplacesCommandToken() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Single("/permission"));
        Harness harness = harness(orchestrator);
        harness.insert("/pe args");

        harness.handleKey('\t');

        assertEquals("/permission args", harness.input().content());
        assertEquals("/permission".length(), harness.input().cursorIndex());
    }

    @Test
    void tabMultipleMatchPrintsTemporaryMenuAndNextInputClearsIt() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Multiple(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/permission", "/permission")
        )));
        Harness harness = harness(orchestrator);
        harness.insert("/p");

        harness.handleKey('\t');

        assertTrue(harness.output().contains("候选: /plan  /permission"));
        assertTrue(harness.completionMenuVisible());

        harness.handleKey('x');

        assertFalse(harness.completionMenuVisible());
        assertEquals("/px", harness.input().content());
    }

    @Test
    void clearVisibleScreenClearsInputAndCompletionMenu() throws Exception {
        FakeOrchestrator orchestrator = new FakeOrchestrator(new SlashCommandCompletion.Multiple(List.of(
                new SlashCommandName("/plan", "/plan"),
                new SlashCommandName("/permission", "/permission")
        )));
        Harness harness = harness(orchestrator);
        harness.insert("/p");
        harness.handleKey('\t');

        harness.tui.clearVisibleScreen();

        assertEquals("", harness.input().content());
        assertFalse(harness.completionMenuVisible());
        assertTrue(harness.output().contains("\u001B[2J"));
    }

    private Harness harness(FakeOrchestrator orchestrator) throws Exception {
        LanternaLunaTui tui = new LanternaLunaTui(new DefaultConversationManager(), orchestrator);
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output, true);
        Terminal terminal = (Terminal) Proxy.newProxyInstance(
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
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    return null;
                }
        );
        Field terminalField = LanternaLunaTui.class.getDeclaredField("terminal");
        terminalField.setAccessible(true);
        terminalField.set(tui, terminal);
        return new Harness(tui, output);
    }

    private static class Harness {
        private final LanternaLunaTui tui;
        private final StringWriter output;

        private Harness(LanternaLunaTui tui, StringWriter output) {
            this.tui = tui;
            this.output = output;
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
    }

    private static class FakeOrchestrator implements ChatOrchestrator {
        private final SlashCommandCompletion completion;

        private FakeOrchestrator(SlashCommandCompletion completion) {
            this.completion = completion;
        }

        @Override public void submitUserMessage(String content) {}
        @Override public void cancelCurrentRun() {}
        @Override public SlashCommandCompletion completeSlashCommand(String input, int cursorIndex) { return completion; }
        @Override public StatusSnapshot status() { return StatusSnapshot.idle("test", "model"); }
    }
}