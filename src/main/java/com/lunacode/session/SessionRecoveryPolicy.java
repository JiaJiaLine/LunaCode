package com.lunacode.session;

import com.lunacode.config.ContextConfig;
import com.lunacode.context.ApproximateContextTokenEstimator;
import com.lunacode.context.ContextTokenEstimator;
import com.lunacode.context.TokenEstimate;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageMetadata;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SessionRecoveryPolicy {
    private static final Duration TIME_GAP_REMINDER_THRESHOLD = Duration.ofHours(24);

    private final Clock clock;
    private final ContextTokenEstimator estimator;

    public SessionRecoveryPolicy() {
        this(Clock.systemDefaultZone(), new ApproximateContextTokenEstimator());
    }

    SessionRecoveryPolicy(Clock clock, ContextTokenEstimator estimator) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.estimator = Objects.requireNonNull(estimator, "estimator");
    }

    public SessionRecoveryResult recover(SessionLoadResult loadResult, ContextConfig contextConfig) {
        Objects.requireNonNull(loadResult, "loadResult");
        ContextConfig effectiveConfig = contextConfig == null ? ContextConfig.defaults() : contextConfig;
        List<String> warnings = new ArrayList<>(loadResult.warnings());
        List<ConversationMessageSnapshot> repaired = truncateUnpairedToolUse(loadResult.messages(), warnings);

        Optional<ConversationMessageSnapshot> timeGapReminder = timeGapReminder(loadResult.info());
        List<ConversationMessageSnapshot> restored = new ArrayList<>(repaired);
        timeGapReminder.ifPresent(restored::add);

        TokenEstimate estimate = estimator.estimateMessages(restored, effectiveConfig);
        if (estimate.estimatedTokens() >= effectiveConfig.budget().forceCompactThresholdTokens()) {
            warnings.add("恢复会话超过上下文预算，已降级为会话摘要提示；原 JSONL 未修改。");
            ConversationMessageSnapshot summary = summaryOnlyMessage(loadResult, estimate);
            return new SessionRecoveryResult(loadResult.id().value(), List.of(summary), warnings, timeGapReminder, false, true);
        }

        return new SessionRecoveryResult(loadResult.id().value(), restored, warnings, timeGapReminder, false, false);
    }

    private List<ConversationMessageSnapshot> truncateUnpairedToolUse(List<ConversationMessageSnapshot> messages, List<String> warnings) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessageSnapshot message = messages.get(i);
            if (message.role() != MessageRole.ASSISTANT) {
                continue;
            }
            List<String> toolUseIds = toolUseIds(message);
            if (toolUseIds.isEmpty()) {
                continue;
            }
            Set<String> followingResults = toolResultIds(messages.subList(i + 1, messages.size()));
            for (String toolUseId : toolUseIds) {
                if (!followingResults.contains(toolUseId)) {
                    warnings.add("会话末尾存在未配对工具调用，已从 assistant 消息开始截断: " + toolUseId);
                    return List.copyOf(messages.subList(0, i));
                }
            }
        }
        return List.copyOf(messages);
    }

    private Optional<ConversationMessageSnapshot> timeGapReminder(SessionInfo info) {
        if (info == null || info.lastActiveAt() == null) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        if (!info.lastActiveAt().plus(TIME_GAP_REMINDER_THRESHOLD).isBefore(now)) {
            return Optional.empty();
        }
        String lastActive = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone())
                .format(info.lastActiveAt());
        String content = """
                [会话时间跨度提醒]
                上次会话活跃时间：%s。
                距离上次会话已经超过 24 小时，期间代码可能发生变化。继续决策前，请重新读取相关文件或状态，不要只依赖恢复的历史内容。
                """.formatted(lastActive).strip();
        return Optional.of(new ConversationMessageSnapshot(
                UUID.randomUUID().toString(),
                MessageRole.USER,
                MessageStatus.COMPLETE,
                now,
                TokenUsage.unknown(),
                content,
                List.of(new ContentBlock.Text(content)),
                ConversationMessageMetadata.empty(),
                null
        ));
    }

    private ConversationMessageSnapshot summaryOnlyMessage(SessionLoadResult loadResult, TokenEstimate estimate) {
        String content = """
                [会话恢复摘要]
                会话 %s 的历史内容过长，估算 token=%d，已跳过完整历史恢复。
                原始 JSONL 文件未修改，必要时请读取会话文件或重新读取项目文件后继续。
                """.formatted(loadResult.id().value(), estimate.estimatedTokens()).strip();
        return new ConversationMessageSnapshot(
                UUID.randomUUID().toString(),
                MessageRole.USER,
                MessageStatus.COMPLETE,
                Instant.now(clock),
                TokenUsage.unknown(),
                content,
                List.of(new ContentBlock.Text(content)),
                ConversationMessageMetadata.empty(),
                null
        );
    }

    private List<String> toolUseIds(ConversationMessageSnapshot message) {
        List<String> ids = new ArrayList<>();
        for (ContentBlock block : message.blocks()) {
            if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                ids.add(toolUse.id());
            }
        }
        return ids;
    }

    private Set<String> toolResultIds(List<ConversationMessageSnapshot> messages) {
        Set<String> ids = new HashSet<>();
        for (ConversationMessageSnapshot message : messages) {
            for (ContentBlock block : message.blocks()) {
                if (block instanceof ContentBlock.ToolResultBlock result) {
                    ids.add(result.toolUseId());
                }
            }
        }
        return ids;
    }
}
