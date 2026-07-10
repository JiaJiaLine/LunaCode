package com.lunacode.tui;

import com.lunacode.command.CommandUiController;
import com.lunacode.command.SlashCommandCompletion;
import com.lunacode.command.SlashCommandName;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.InternalMessage;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.orchestrator.ChatOrchestrator;
import com.lunacode.orchestrator.StatusSnapshot;
import com.lunacode.runtime.AgentMode;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LunaCode 的滚动式终端界面。
 *
 * <p>完成的消息只追加到终端历史，动画、候选、上下文与输入行统一放在底部实时区域。</p>
 */
public class LanternaLunaTui implements LunaTui, CommandUiController {
    private static final String ESC = "\u001B";
    private static final long ESC_SEQUENCE_TIMEOUT_MILLIS = 100L;
    private static final int DEFAULT_TERMINAL_WIDTH = 80;

    private final ConversationManager conversationManager;
    private final ChatOrchestrator orchestrator;
    private final InputLineBuffer input = new InputLineBuffer();
    private final Set<String> startedMessages = new HashSet<>();
    private final Set<String> finishedMessages = new HashSet<>();
    private final Set<String> openAssistantLines = new HashSet<>();
    private final Map<String, Integer> printedLengths = new HashMap<>();
    private final Clock clock;
    private final AnimationTicker ticker;
    private final TerminalProfile profileOverride;
    private final LunaTheme theme;
    private final TerminalLayout layout;
    private final LiveRegionRenderer liveRegionRenderer;
    private final Spinner spinner;
    private final TuiActivityTracker activityTracker;
    private final BuildInfo buildInfo;
    private final AtomicBoolean renderFailed = new AtomicBoolean();

    private String lastPrintedStatusKey;
    private String lastDisplayedAssistantError;
    private Terminal terminal;
    private Attributes originalAttributes;
    private TerminalProfile terminalProfile;
    private AutoCloseable eventSubscription;
    private volatile boolean running;
    private boolean completionMenuVisible;
    private List<SlashCommandName> completionCandidates = List.of();
    private boolean restored;

    public LanternaLunaTui(ConversationManager conversationManager, ChatOrchestrator orchestrator) {
        this(
                conversationManager,
                orchestrator,
                Clock.systemUTC(),
                new ScheduledAnimationTicker(),
                null,
                new LunaTheme(),
                new TerminalLayout(),
                new LiveRegionRenderer(),
                new Spinner(),
                new TuiActivityTracker(),
                BuildInfo.load()
        );
    }

    LanternaLunaTui(
            ConversationManager conversationManager,
            ChatOrchestrator orchestrator,
            Clock clock,
            AnimationTicker ticker,
            TerminalProfile profileOverride,
            LunaTheme theme,
            TerminalLayout layout,
            LiveRegionRenderer liveRegionRenderer,
            Spinner spinner,
            TuiActivityTracker activityTracker,
            BuildInfo buildInfo
    ) {
        this.conversationManager = conversationManager;
        this.orchestrator = orchestrator;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.ticker = ticker == null ? new ScheduledAnimationTicker() : ticker;
        this.profileOverride = profileOverride;
        this.theme = theme == null ? new LunaTheme() : theme;
        this.layout = layout == null ? new TerminalLayout() : layout;
        this.liveRegionRenderer = liveRegionRenderer == null ? new LiveRegionRenderer() : liveRegionRenderer;
        this.spinner = spinner == null ? new Spinner() : spinner;
        this.activityTracker = activityTracker == null ? new TuiActivityTracker() : activityTracker;
        this.buildInfo = buildInfo == null ? new BuildInfo("dev") : buildInfo;
    }

    @Override
    public void start() {
        try {
            terminal = TerminalBuilder.builder()
                    .name("LunaCode")
                    .system(true)
                    .jna(true)
                    .build();
            terminalProfile = profileOverride == null
                    ? TerminalProfile.detect(terminal, System.getenv(), terminal.encoding())
                    : profileOverride;
            originalAttributes = terminal.enterRawMode();
            if (orchestrator != null) {
                orchestrator.setCommandUiController(this);
            }
            subscribeToAgentEvents();
            running = true;
            printBrandCard(currentStatus());
            requestRender();
            eventLoop();
        } catch (IOException e) {
            throw new TuiException("TUI startup failed", e);
        } finally {
            restoreTerminal();
        }
    }

    public void requestRender() {
        Terminal currentTerminal = terminal;
        if (currentTerminal == null) {
            return;
        }
        try {
            List<InternalMessage> messages = conversationManager == null
                    ? List.of()
                    : conversationManager.snapshot();
            render(messages, currentStatus());
        } catch (RuntimeException failure) {
            handleRenderFailure(failure);
        }
    }

    @Override
    public synchronized void render(List<InternalMessage> messages, StatusSnapshot status) {
        if (terminal == null) {
            return;
        }
        List<InternalMessage> safeMessages = messages == null ? List.of() : messages;
        StatusSnapshot safeStatus = status == null ? StatusSnapshot.idle("", "") : status;
        Instant now = clock.instant();
        activityTracker.synchronize(safeStatus, now);

        PrintWriter writer = terminal.writer();
        for (InternalMessage message : safeMessages) {
            renderMessage(writer, message);
        }
        boolean assistantStreaming = hasVisibleStreamingAssistant(safeMessages);
        List<TuiActivity> completedActivities = assistantStreaming
                ? List.of()
                : activityTracker.drainCompletedRecords();
        boolean statusPrintable = !assistantStreaming && shouldPrintStatus(safeStatus);
        printCompletedActivities(writer, completedActivities);

        if (statusPrintable) {
            printStatus(writer, safeStatus);
        } else {
            lastPrintedStatusKey = null;
        }

        if (assistantStreaming) {
            clearLiveRegion(writer);
        } else {
            renderLiveRegion(writer, liveFrame(safeStatus, now));
        }
        updateTicker(!assistantStreaming && activityTracker.hasAnimatedActivity());
        writer.flush();
        terminal.flush();
    }

    @Override
    public void showFatalError(String summary) {
        String safeSummary = summary == null || summary.isBlank() ? "LunaCode 启动失败" : summary;
        System.err.println(TerminalText.singleLine(safeSummary));
    }

    private void subscribeToAgentEvents() {
        if (orchestrator == null) {
            eventSubscription = () -> {
            };
            return;
        }
        eventSubscription = orchestrator.observeAgentEvents(
                event -> activityTracker.onAgentEvent(event, clock.instant())
        );
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
                requestRender();
            } else {
                handleKey(key);
            }
        }
    }

    private boolean handleEscapeSequence(NonBlockingReader reader) throws IOException {
        int second = reader.read(ESC_SEQUENCE_TIMEOUT_MILLIS);
        if (second == NonBlockingReader.READ_EXPIRED) {
            if (isBusy()) {
                orchestrator.backgroundCurrentSubAgentOrCancel();
                return true;
            }
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

    private boolean isBusy() {
        if (orchestrator == null) {
            return false;
        }
        String state = orchestrator.status().state();
        return "responding".equals(state)
                || "tool_running".equals(state)
                || "compacting".equals(state)
                || "waiting_user".equals(state)
                || "waiting_permission".equals(state);
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

    private synchronized void handleKey(int key) {
        if (key == '\t') {
            handleTabCompletion();
            requestRender();
            return;
        }
        if (key == '\r' || key == '\n') {
            completionMenuVisible = false;
            completionCandidates = List.of();
            String content = input.consume().strip();
            if (terminal != null) {
                beforePersistentOutput(terminal.writer());
            }
            if (!content.isEmpty() && orchestrator != null) {
                orchestrator.submitUserMessage(content);
            }
            requestRender();
            return;
        }
        if (key == 127 || key == '\b') {
            hideCompletionMenu();
            input.backspace();
            requestRender();
            return;
        }
        if (!Character.isISOControl(key)) {
            hideCompletionMenu();
            input.insert(key);
            requestRender();
        }
    }

    private void handleTabCompletion() {
        hideCompletionMenu();
        if (orchestrator == null) {
            return;
        }
        SlashCommandCompletion completion = orchestrator.completeSlashCommand(
                input.content(),
                input.cursorIndex()
        );
        if (completion instanceof SlashCommandCompletion.Single single) {
            input.replaceCommandToken(single.replacement());
            return;
        }
        if (completion instanceof SlashCommandCompletion.Multiple multiple) {
            completionCandidates = List.copyOf(multiple.candidates());
            completionMenuVisible = !completionCandidates.isEmpty();
        }
    }

    private void hideCompletionMenu() {
        completionMenuVisible = false;
        completionCandidates = List.of();
    }

    private boolean renderMessage(PrintWriter writer, InternalMessage message) {
        if (message == null) {
            return false;
        }
        if (message.role() == MessageRole.USER) {
            if (!startedMessages.add(message.id())) {
                return false;
            }
            beforePersistentOutput(writer);
            writer.print(styledSymbol(TuiSymbol.USER, TuiTone.USER));
            writer.print(" ");
            writer.println(displayMultiline(message.content()));
            lastDisplayedAssistantError = null;
            return true;
        }
        if (message.role() == MessageRole.TOOL || message.role() != MessageRole.ASSISTANT) {
            return false;
        }

        String content = message.content() == null ? "" : message.content();
        boolean firstVisibleContent = !content.isEmpty() && !startedMessages.contains(message.id());
        boolean terminalWithoutContent = content.isEmpty()
                && message.status() == MessageStatus.ERROR
                && !startedMessages.contains(message.id());
        if (content.isEmpty() && message.status() == MessageStatus.COMPLETE) {
            startedMessages.add(message.id());
            printedLengths.put(message.id(), 0);
            finishedMessages.add(message.id());
            return false;
        }
        if (firstVisibleContent || terminalWithoutContent) {
            beforePersistentOutput(writer);
            startedMessages.add(message.id());
            printedLengths.put(message.id(), 0);
            writer.print(styledSymbol(TuiSymbol.ASSISTANT, TuiTone.ASSISTANT));
            writer.print(" ");
            openAssistantLines.add(message.id());
        }

        boolean changed = firstVisibleContent || terminalWithoutContent;
        int printed = printedLengths.getOrDefault(message.id(), 0);
        if (content.length() > printed) {
            if (!openAssistantLines.contains(message.id())) {
                beforePersistentOutput(writer);
                writer.print(styledSymbol(TuiSymbol.ASSISTANT, TuiTone.ASSISTANT));
                writer.print(" ");
                openAssistantLines.add(message.id());
                changed = true;
            }
            writer.print(displayMultiline(content.substring(printed)));
            printedLengths.put(message.id(), content.length());
            changed = true;
        }

        if (message.status() == MessageStatus.ERROR && finishedMessages.add(message.id())) {
            if (openAssistantLines.remove(message.id())) {
                writer.println();
            }
            writer.print(styledSymbol(TuiSymbol.FAILURE, TuiTone.ERROR));
            writer.print(" ");
            String summary = displaySingleLine(safeText(message.errorSummary(), "模型回复失败"));
            writer.println(theme.style(
                    TuiTone.ERROR,
                    summary,
                    terminal,
                    profile()
            ));
            lastDisplayedAssistantError = summary;
            return true;
        }
        if (message.status() == MessageStatus.COMPLETE && finishedMessages.add(message.id())) {
            if (openAssistantLines.remove(message.id())) {
                writer.println();
                return true;
            }
            return changed;
        }
        return changed;
    }

    private void printCompletedActivities(PrintWriter writer, List<TuiActivity> records) {
        for (TuiActivity record : records) {
            if (record == null) {
                continue;
            }
            beforePersistentOutput(writer);
            boolean success = record.phase() == ActivityPhase.SUCCESS;
            TuiTone tone = success ? TuiTone.SUCCESS : TuiTone.ERROR;
            TuiSymbol symbol = success ? TuiSymbol.SUCCESS : TuiSymbol.FAILURE;
            StringBuilder detail = new StringBuilder();
            if (!success && !record.errorSummary().isBlank()) {
                detail.append(displaySingleLine(record.errorSummary()));
            }
            if (!record.detail().isBlank()) {
                if (!detail.isEmpty()) {
                    detail.append(separator());
                }
                detail.append(displaySingleLine(record.detail()));
            }
            String duration = record.finalDuration() == null
                    ? ""
                    : separator() + formatElapsed(record.finalDuration());
            String compact = composeWithProtectedSuffix(
                    displaySingleLine(record.title()),
                    detail.toString(),
                    duration,
                    Math.max(1, terminalWidth() - 2)
            );
            writer.print(styledSymbol(symbol, tone));
            writer.print(" ");
            writer.println(theme.style(tone, compact, terminal, profile()));
        }
    }

    private LiveRegionFrame liveFrame(StatusSnapshot status, Instant now) {
        int width = cursorControlEnabled()
                ? Math.max(1, terminalWidth() - 1)
                : terminalWidth();
        List<String> activityLines = activityTracker.primaryActivity()
                .map(activity -> List.of(activityLine(activity, now, width)))
                .orElseGet(List::of);
        List<String> completionLines = completionMenuVisible
                ? themedCompletionLines(layout.completionLines(completionCandidates, width, profile()))
                : List.of();
        PromptFrame prompt = layout.prompt(
                promptContext(status),
                input.content(),
                input.cursorIndex(),
                width,
                profile()
        );
        return new LiveRegionFrame(
                activityLines,
                completionLines,
                theme.style(TuiTone.MUTED, prompt.contextLine(), terminal, profile()),
                themedPrompt(prompt.promptLine()),
                prompt.cursorColumnsFromEnd()
        );
    }

    private String activityLine(TuiActivity activity, Instant now, int width) {
        Duration elapsed = activity.elapsedAt(now);
        String prefix = spinner.frame(elapsed, profile())
                + " " + displaySingleLine(activity.title());
        String elapsedText = formatElapsed(elapsed);
        String suffix = separator() + elapsedText
                + separator() + (activity.kind() == ActivityKind.BACKGROUND ? "Esc 退出" : "Esc 取消");
        String raw = composeWithProtectedSuffix(
                prefix,
                displaySingleLine(activity.detail()),
                suffix,
                width
        );
        TuiTone tone = activity.kind() == ActivityKind.TOOL
                ? TuiTone.TOOL
                : TuiTone.BRAND_SECONDARY;
        return theme.style(
                tone,
                raw,
                terminal,
                profile()
        );
    }

    private String composeWithProtectedSuffix(
            String prefix,
            String detail,
            String suffix,
            int maxColumns
    ) {
        int width = Math.max(1, maxColumns);
        String safePrefix = displaySingleLine(prefix);
        String safeDetail = displaySingleLine(detail);
        String safeSuffix = displaySingleLine(suffix);
        int fixedWidth = layout.columns(safePrefix) + layout.columns(safeSuffix);
        if (fixedWidth > width) {
            String elapsedOnly = elapsedSuffix(safeSuffix);
            int elapsedWidth = layout.columns(elapsedOnly);
            if (elapsedWidth < width) {
                return layout.truncate(
                        safePrefix,
                        width - elapsedWidth,
                        truncationMarker()
                ) + elapsedOnly;
            }
            return layout.truncate(safePrefix, width, truncationMarker());
        }
        if (safeDetail.isBlank()) {
            return safePrefix + safeSuffix;
        }
        int detailBudget = width - fixedWidth - layout.columns(separator());
        if (detailBudget <= 0) {
            return safePrefix + safeSuffix;
        }
        return safePrefix
                + separator()
                + layout.truncate(safeDetail, detailBudget, truncationMarker())
                + safeSuffix;
    }

    private String elapsedSuffix(String suffix) {
        int first = suffix.indexOf(separator());
        if (first < 0) {
            return suffix;
        }
        int second = suffix.indexOf(separator(), first + separator().length());
        return second < 0 ? suffix : suffix.substring(0, second);
    }

    private List<String> themedCompletionLines(List<String> lines) {
        List<String> styled = new ArrayList<>(lines.size());
        for (String line : lines) {
            styled.add(themedCompletionLine(line));
        }
        return List.copyOf(styled);
    }

    private String themedCompletionLine(String line) {
        line = displaySingleLine(line);
        StringBuilder styled = new StringBuilder();
        String[] segments = line.split(" {2,}", -1);
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                styled.append("  ");
            }
            String segment = segments[index];
            String prefix = "";
            if (segment.startsWith("候选: ")) {
                prefix = "候选: ";
                segment = segment.substring(prefix.length());
                styled.append(theme.style(TuiTone.MUTED, prefix, terminal, profile()));
            }
            int ownerAt = segment.indexOf(" -> ");
            if (ownerAt < 0) {
                styled.append(theme.style(TuiTone.BRAND_PRIMARY, segment, terminal, profile()));
            } else {
                styled.append(theme.style(
                        TuiTone.BRAND_PRIMARY,
                        segment.substring(0, ownerAt),
                        terminal,
                        profile()
                ));
                styled.append(theme.style(
                        TuiTone.MUTED,
                        segment.substring(ownerAt),
                        terminal,
                        profile()
                ));
            }
        }
        return styled.toString();
    }

    private String themedPrompt(String promptLine) {
        if (promptLine == null || promptLine.isEmpty()) {
            return "";
        }
        int prefixLength = promptLine.startsWith("> ") || promptLine.startsWith("❯ ") ? 2 : 1;
        return theme.style(
                TuiTone.USER,
                promptLine.substring(0, Math.min(prefixLength, promptLine.length())),
                terminal,
                profile()
        ) + promptLine.substring(Math.min(prefixLength, promptLine.length()));
    }

    private void printBrandCard(StatusSnapshot status) {
        if (terminal == null) {
            return;
        }
        PrintWriter writer = terminal.writer();
        beforePersistentOutput(writer);
        int width = terminalWidth();
        String brand = theme.symbol(TuiSymbol.BRAND_MOON, profile())
                + " LunaCode " + displaySingleLine(buildInfo.version());
        String model = "模型  " + displaySingleLine(safeText(status == null ? null : status.model(), "未配置"));
        String workdirLabel = "目录  ";
        String workdir = workdirLabel + layout.truncateLeft(
                displaySingleLine(Path.of("").toAbsolutePath().normalize().toString()),
                Math.max(0, width - layout.columns(workdirLabel)),
                leftTruncationMarker()
        );
        String hint = width < 32
                ? "/help" + separator() + "Esc"
                : "/help 查看命令" + separator() + "Esc 退出/取消";

        writer.println(theme.style(
                TuiTone.BRAND_PRIMARY,
                layout.truncate(brand, width, truncationMarker()),
                terminal,
                profile()
        ));
        writer.println(theme.style(
                TuiTone.BRAND_SECONDARY,
                layout.truncate(model, width, truncationMarker()),
                terminal,
                profile()
        ));
        writer.println(theme.style(
                TuiTone.MUTED,
                layout.truncate(workdir, width, truncationMarker()),
                terminal,
                profile()
        ));
        writer.println(theme.style(
                TuiTone.MUTED,
                layout.truncate(hint, width, truncationMarker()),
                terminal,
                profile()
        ));
        writer.flush();
        terminal.flush();
    }

    private boolean shouldPrintStatus(StatusSnapshot status) {
        if (status == null) {
            return false;
        }
        return "waiting_user".equals(status.state())
                || "waiting_permission".equals(status.state())
                || "cancelled".equals(status.state())
                || "error".equals(status.state())
                || "warning".equals(status.state())
                || ("idle".equals(status.state())
                && status.errorSummary() != null
                && !status.errorSummary().isBlank());
    }

    private void printStatus(PrintWriter writer, StatusSnapshot status) {
        if (!shouldPrintStatus(status)) {
            return;
        }
        String statusKey = statusPrintKey(status);
        if (statusKey.equals(lastPrintedStatusKey)) {
            return;
        }
        String message = safeStatusMessage(status);
        if ("error".equals(status.state()) && message.equals(lastDisplayedAssistantError)) {
            lastDisplayedAssistantError = null;
            lastPrintedStatusKey = statusKey;
            return;
        }
        lastPrintedStatusKey = statusKey;
        beforePersistentOutput(writer);

        String state = status.state();
        TuiSymbol symbol;
        TuiTone tone;
        String label;
        if ("waiting_user".equals(state)) {
            symbol = TuiSymbol.QUESTION;
            tone = TuiTone.WARNING;
            label = "等待回答";
        } else if ("waiting_permission".equals(state)) {
            symbol = TuiSymbol.PERMISSION;
            tone = TuiTone.WARNING;
            label = "权限确认 (" + status.permissionMode().configValue() + ")";
        } else if ("cancelled".equals(state)) {
            symbol = TuiSymbol.WARNING;
            tone = TuiTone.WARNING;
            label = "已取消";
        } else if ("error".equals(state)) {
            symbol = TuiSymbol.FAILURE;
            tone = TuiTone.ERROR;
            label = "错误";
        } else if ("warning".equals(state)) {
            symbol = TuiSymbol.WARNING;
            tone = TuiTone.WARNING;
            label = "警告";
        } else {
            symbol = TuiSymbol.BRAND_MOON;
            tone = TuiTone.BRAND_SECONDARY;
            label = "信息";
        }

        String session = status.sessionShortId() == null || status.sessionShortId().isBlank()
                ? ""
                : separator() + "s:" + displaySingleLine(status.sessionShortId());
        String[] lines = message.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String raw = index == 0
                    ? label + session + separator() + lines[index]
                    : lines[index];
            if (index == 0) {
                writer.print(styledSymbol(symbol, tone));
                writer.print(" ");
            } else {
                writer.print("  ");
            }
            writer.println(theme.style(
                    index == 0 ? tone : TuiTone.MUTED,
                    layout.truncate(raw, Math.max(1, terminalWidth() - 2), truncationMarker()),
                    terminal,
                    profile()
            ));
        }
    }

    private String statusPrintKey(StatusSnapshot status) {
        return status.state()
                + "\u0000" + status.agentMode()
                + "\u0000" + status.permissionMode().configValue()
                + "\u0000" + statusText(status.errorSummary())
                + "\u0000" + statusText(status.toolName())
                + "\u0000" + statusText(status.toolSummary())
                + "\u0000" + statusText(status.sessionShortId());
    }

    private String promptContext(StatusSnapshot status) {
        if (status == null) {
            return "";
        }
        String agentMode = status.agentMode() == AgentMode.PLAN ? "Plan" : "Agent";
        return layout.compactContext(
                displaySingleLine(status.model()),
                agentMode,
                displaySingleLine(status.permissionMode().configValue()),
                displaySingleLine(status.sessionShortId())
        ).replace(" · ", separator());
    }

    private String safeStatusMessage(StatusSnapshot status) {
        String message = status.errorSummary();
        if ((message == null || message.isBlank()) && status.toolSummary() != null) {
            message = status.toolSummary();
        }
        if (message != null && !message.isBlank()) {
            return displayMultiline(message.strip());
        }
        return switch (status.state()) {
            case "waiting_user" -> "请回答后继续";
            case "waiting_permission" -> "请确认是否允许本次操作";
            case "cancelled" -> "当前操作已停止";
            case "error" -> "本次操作失败";
            case "warning" -> "请检查当前运行状态";
            default -> "状态已更新";
        };
    }

    private String styledSymbol(TuiSymbol symbol, TuiTone tone) {
        return theme.style(tone, theme.symbol(symbol, profile()), terminal, profile());
    }

    private boolean hasVisibleStreamingAssistant(List<InternalMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            InternalMessage message = messages.get(index);
            if (message != null && message.role() == MessageRole.ASSISTANT) {
                return message.status() == MessageStatus.STREAMING
                        && message.content() != null
                        && !message.content().isEmpty();
            }
        }
        return false;
    }

    private void updateTicker(boolean shouldRun) {
        if (shouldRun && cursorControlEnabled()) {
            if (!ticker.running()) {
                ticker.start(this::requestRender);
            }
            return;
        }
        ticker.stop();
    }

    private void handleRenderFailure(RuntimeException failure) {
        running = false;
        try {
            ticker.stop();
        } catch (RuntimeException ignored) {
            // 继续让事件循环进入 finally 恢复终端。
        }
        if (renderFailed.compareAndSet(false, true)) {
            String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            System.err.println("LunaCode 终端渲染失败: " + TerminalText.singleLine(detail));
        }
    }

    private void beforePersistentOutput(PrintWriter writer) {
        liveRegionRenderer.beforePersistentOutput(
                writer,
                terminalWidth(),
                cursorControlEnabled()
        );
    }

    private void renderLiveRegion(PrintWriter writer, LiveRegionFrame frame) {
        liveRegionRenderer.render(
                writer,
                frame,
                terminalWidth(),
                cursorControlEnabled()
        );
    }

    private void clearLiveRegion(PrintWriter writer) {
        liveRegionRenderer.clear(
                writer,
                terminalWidth(),
                cursorControlEnabled()
        );
    }

    private boolean cursorControlEnabled() {
        if (!profile().interactive() || terminal == null) {
            return false;
        }
        String type = terminal.getType();
        return type == null
                || !type.toLowerCase(Locale.ROOT).startsWith(Terminal.TYPE_DUMB);
    }

    private String formatElapsed(Duration duration) {
        Duration safe = duration == null || duration.isNegative() ? Duration.ZERO : duration;
        long millis = safe.toMillis();
        if (millis < 1000L) {
            return millis == 0L ? "0.0s" : String.format(Locale.ROOT, "%.1fs", millis / 1000.0d);
        }
        long seconds = safe.toSeconds();
        if (seconds < 60L) {
            return seconds + "s";
        }
        return (seconds / 60L) + "m " + String.format(Locale.ROOT, "%02ds", seconds % 60L);
    }

    private String truncationMarker() {
        return theme.symbol(TuiSymbol.TRUNCATE_RIGHT, profile());
    }

    private String leftTruncationMarker() {
        return theme.symbol(TuiSymbol.TRUNCATE_LEFT, profile());
    }

    private String separator() {
        return profile().unicodeEnabled() ? " · " : " | ";
    }

    private String displaySingleLine(String value) {
        String safe = TerminalText.singleLine(value);
        return profile().unicodeEnabled() ? safe : safe.replace(" · ", " | ");
    }

    private String displayMultiline(String value) {
        String safe = TerminalText.multiline(value);
        return profile().unicodeEnabled() ? safe : safe.replace(" · ", " | ");
    }

    private int terminalWidth() {
        if (terminal == null) {
            return DEFAULT_TERMINAL_WIDTH;
        }
        int width = terminal.getWidth();
        return width > 0 ? width : DEFAULT_TERMINAL_WIDTH;
    }

    private TerminalProfile profile() {
        if (terminalProfile != null) {
            return terminalProfile;
        }
        if (profileOverride != null) {
            return profileOverride;
        }
        return new TerminalProfile(false, false, false, 0);
    }

    private StatusSnapshot currentStatus() {
        return orchestrator == null ? StatusSnapshot.idle("", "") : orchestrator.status();
    }

    private String statusText(String value) {
        return value == null ? "" : value;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    @Override
    public synchronized void clearVisibleScreen() {
        ticker.stop();
        input.clear();
        hideCompletionMenu();
        lastPrintedStatusKey = null;
        activityTracker.clear();
        suppressExistingMessages();
        liveRegionRenderer.reset();
        if (terminal == null) {
            return;
        }

        PrintWriter writer = terminal.writer();
        if (cursorControlEnabled()) {
            writer.print(ESC + "[2J" + ESC + "[H");
        } else {
            writer.println();
            writer.println("--- 已清屏 ---");
        }
        writer.flush();
        printBrandCard(currentStatus());
        requestRender();
    }

    private void suppressExistingMessages() {
        startedMessages.clear();
        finishedMessages.clear();
        openAssistantLines.clear();
        printedLengths.clear();
        if (conversationManager == null) {
            return;
        }
        for (InternalMessage message : conversationManager.snapshot()) {
            if (message == null) {
                continue;
            }
            startedMessages.add(message.id());
            printedLengths.put(message.id(), message.content() == null ? 0 : message.content().length());
            if (message.status() != MessageStatus.STREAMING) {
                finishedMessages.add(message.id());
            }
        }
    }

    private synchronized void restoreTerminal() {
        if (restored) {
            return;
        }
        restored = true;
        running = false;
        try {
            ticker.stop();
        } catch (RuntimeException ignored) {
            // 继续尝试关闭 Ticker。
        }
        try {
            ticker.close();
        } catch (RuntimeException ignored) {
            // Ticker 故障不能阻断订阅和终端属性恢复。
        }
        closeEventSubscription();
        if (orchestrator != null) {
            try {
                orchestrator.setCommandUiController(null);
            } catch (RuntimeException ignored) {
                // 控制器解绑失败不影响终端恢复。
            }
        }

        Terminal currentTerminal = terminal;
        if (currentTerminal == null) {
            return;
        }
        try {
            PrintWriter writer = currentTerminal.writer();
            clearLiveRegion(writer);
            writer.print(theme.reset(profile()));
            writer.println();
            writer.flush();
            currentTerminal.flush();
        } catch (Exception ignored) {
            // 输出清理失败后仍必须继续恢复 raw mode。
        }
        try {
            if (originalAttributes != null) {
                currentTerminal.setAttributes(originalAttributes);
            }
        } catch (Exception ignored) {
            // 属性恢复采用 best effort，随后仍尝试关闭终端。
        }
        try {
            currentTerminal.close();
        } catch (Exception ignored) {
            // 关闭阶段已没有更安全的恢复动作。
        } finally {
            terminal = null;
        }
    }

    private void closeEventSubscription() {
        if (eventSubscription == null) {
            return;
        }
        try {
            eventSubscription.close();
        } catch (Exception ignored) {
            // 观察者取消失败不能阻断终端属性恢复。
        } finally {
            eventSubscription = null;
        }
    }

    public static class TuiException extends RuntimeException {
        public TuiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
