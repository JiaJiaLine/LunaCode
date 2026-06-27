package com.lunacode.tui;

import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.InternalMessage;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.orchestrator.ChatOrchestrator;
import com.lunacode.orchestrator.StatusSnapshot;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LanternaLunaTui implements LunaTui {
    private static final String ESC = "\u001B";
    private static final long ESC_SEQUENCE_TIMEOUT_MILLIS = 100L;

    private final ConversationManager conversationManager;
    private final ChatOrchestrator orchestrator;
    private final InputLineBuffer input = new InputLineBuffer();
    private final Set<String> startedMessages = new HashSet<>();
    private final Set<String> finishedMessages = new HashSet<>();
    private final Map<String, Integer> printedLengths = new HashMap<>();
    private Terminal terminal;
    private Attributes originalAttributes;
    private volatile boolean running;
    private boolean promptVisible;

    public LanternaLunaTui(ConversationManager conversationManager, ChatOrchestrator orchestrator) {
        this.conversationManager = conversationManager;
        this.orchestrator = orchestrator;
    }

    @Override
    public void start() {
        try {
            terminal = TerminalBuilder.builder()
                    .name("LunaCode")
                    .system(true)
                    .jna(true)
                    .build();
            originalAttributes = terminal.enterRawMode();
            running = true;
            printBanner();
            requestRender();
            eventLoop();
        } catch (IOException e) {
            throw new TuiException("TUI 启动失败", e);
        } finally {
            restoreTerminal();
        }
    }

    public void requestRender() {
        if (terminal == null) {
            return;
        }
        render(conversationManager.snapshot(), orchestrator.status());
    }

    @Override
    public synchronized void render(List<InternalMessage> messages, StatusSnapshot status) {
        PrintWriter writer = terminal.writer();
        boolean changed = false;
        for (InternalMessage message : messages) {
            changed |= renderMessage(writer, message);
        }
        if (changed || status.toolName() != null) {
            printStatus(writer, status);
        }
        if (!"responding".equals(status.state()) && !"tool_running".equals(status.state())) {
            drawPrompt(writer);
        }
        writer.flush();
        terminal.flush();
    }

    @Override
    public void showFatalError(String summary) {
        System.err.println(summary);
    }

    private void eventLoop() throws IOException {
        NonBlockingReader reader = terminal.reader();
        while (running) {
            int key = reader.read(50L);
            if (key == NonBlockingReader.READ_EXPIRED) {
                continue;
            }
            if (key == NonBlockingReader.EOF) {
                running = false;
                break;
            }
            if (key == 27) {
                if (!handleEscapeSequence(reader)) {
                    running = false;
                    break;
                }
            } else {
                handleKey(key);
            }
            requestRender();
        }
    }

    private boolean handleEscapeSequence(NonBlockingReader reader) throws IOException {
        int second = reader.read(ESC_SEQUENCE_TIMEOUT_MILLIS);
        if (second == NonBlockingReader.READ_EXPIRED) {
            return false;
        }
        if (second == NonBlockingReader.EOF) {
            running = false;
            return true;
        }
        if (second != '[' && second != 'O') {
            return true;
        }

        int third = reader.read(ESC_SEQUENCE_TIMEOUT_MILLIS);
        if (third == NonBlockingReader.READ_EXPIRED || third == NonBlockingReader.EOF) {
            return true;
        }
        handleEscapeCommand(third, reader);
        return true;
    }

    private void handleEscapeCommand(int command, NonBlockingReader reader) throws IOException {
        switch (command) {
            case 'C' -> input.moveRight();
            case 'D' -> input.moveLeft();
            case 'H' -> input.moveHome();
            case 'F' -> input.moveEnd();
            case '1', '7' -> consumeTildeSequence(reader, input::moveHome);
            case '4', '8' -> consumeTildeSequence(reader, input::moveEnd);
            case '3' -> consumeTildeSequence(reader, input::delete);
            default -> {
            }
        }
    }

    private void consumeTildeSequence(NonBlockingReader reader, Runnable action) throws IOException {
        int next = reader.read(ESC_SEQUENCE_TIMEOUT_MILLIS);
        if (next == '~') {
            action.run();
        }
    }

    private void handleKey(int key) {
        if (key == '\r' || key == '\n') {
            String content = input.consume().strip();
            clearPromptLine(terminal.writer());
            promptVisible = false;
            if (!content.isEmpty()) {
                orchestrator.submitUserMessage(content);
            } else {
                drawPrompt(terminal.writer());
            }
            return;
        }
        if (key == 127 || key == '\b') {
            input.backspace();
            drawPrompt(terminal.writer());
            return;
        }
        if (!Character.isISOControl(key)) {
            input.insert(key);
            drawPrompt(terminal.writer());
        }
    }

    private boolean renderMessage(PrintWriter writer, InternalMessage message) {
        if (message.role() == MessageRole.USER) {
            if (startedMessages.add(message.id())) {
                clearPromptLine(writer);
                writer.println("You [complete] " + message.content());
                promptVisible = false;
                return true;
            }
            return false;
        }

        if (message.role() == MessageRole.TOOL) {
            if (startedMessages.add(message.id())) {
                clearPromptLine(writer);
                writer.println("Tool [complete] " + message.content());
                promptVisible = false;
                return true;
            }
            return false;
        }

        if (message.role() != MessageRole.ASSISTANT) {
            return false;
        }

        boolean changed = false;
        if (startedMessages.add(message.id())) {
            writer.print("Luna [streaming] ");
            printedLengths.put(message.id(), 0);
            promptVisible = false;
            changed = true;
        }

        int printed = printedLengths.getOrDefault(message.id(), 0);
        String content = message.content() == null ? "" : message.content();
        if (content.length() > printed) {
            writer.print(content.substring(printed));
            printedLengths.put(message.id(), content.length());
            changed = true;
        }

        if (message.status() == MessageStatus.ERROR) {
            if (finishedMessages.add(message.id())) {
                String summary = message.errorSummary() == null ? "未知错误" : message.errorSummary();
                writer.println();
                writer.println("Luna [error] " + summary);
                changed = true;
            }
            return changed;
        }

        if (message.status() == MessageStatus.COMPLETE && finishedMessages.add(message.id())) {
            writer.println();
            changed = true;
        }
        return changed;
    }

    private void printBanner() {
        PrintWriter writer = terminal.writer();
        writer.println("LunaCode - 按 Esc 退出");
        writer.println("----------------------------------------");
        writer.flush();
        terminal.flush();
    }

    private void printStatus(PrintWriter writer, StatusSnapshot status) {
        if ("responding".equals(status.state())) {
            return;
        }
        String inputTokens = status.inputTokens() == null ? "?" : status.inputTokens().toString();
        String outputTokens = status.outputTokens() == null ? "?" : status.outputTokens().toString();
        writer.println("[status] provider=" + status.provider()
                + " model=" + status.model()
                + " tokens=" + inputTokens + "/" + outputTokens
                + " state=" + status.state());
        if (status.toolName() != null) {
            writer.println("[tool] name=" + status.toolName() + " state=" + status.state()
                    + (status.toolSummary() == null ? "" : " summary=" + status.toolSummary()));
        }
    }

    private void drawPrompt(PrintWriter writer) {
        writer.print("\r" + ESC + "[K> " + input.content());
        int columnsAfterCursor = input.columnsAfterCursor();
        if (columnsAfterCursor > 0) {
            writer.print(ESC + "[" + columnsAfterCursor + "D");
        }
        writer.flush();
        terminal.flush();
        promptVisible = true;
    }

    private void clearPromptLine(PrintWriter writer) {
        if (promptVisible) {
            writer.print("\r" + ESC + "[K");
        }
    }

    private void restoreTerminal() {
        if (terminal == null) {
            return;
        }
        try {
            PrintWriter writer = terminal.writer();
            if (promptVisible) {
                writer.println();
            }
            writer.flush();
            if (originalAttributes != null) {
                terminal.setAttributes(originalAttributes);
            }
            terminal.close();
        } catch (IOException ignored) {
            // 退出阶段无法恢复，忽略即可。
        }
    }

    public static class TuiException extends RuntimeException {
        public TuiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
