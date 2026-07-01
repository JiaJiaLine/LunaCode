package com.lunacode.context;

import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationMessageMetadata;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.conversation.TokenUsage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HistoryCompactor {
    private final SummaryPromptBuilder promptBuilder = new SummaryPromptBuilder();
    private final PromptTooLongRetryPolicy retryPolicy = new PromptTooLongRetryPolicy();
    private final RestoredFileContextBuilder restoredFileContextBuilder = new RestoredFileContextBuilder();

    public CompactionRewrite compact(HistoryCompactionRequest request) {
        if (request.messages().size() <= 1) {
            return CompactionRewrite.failure("当前历史太短，没有可压缩的早期消息。");
        }
        RetentionSplit split = splitForRetention(request.messages(), request.contextConfig());
        List<ConversationMessageSnapshot> toSummarize = new ArrayList<>(split.toSummarize());
        List<ConversationMessageSnapshot> retained = new ArrayList<>(split.retained());
        for (ConversationMessageSnapshot message : split.retained()) {
            if (message.metadata().contextSummary()) {
                toSummarize.add(message);
                retained.remove(message);
            }
        }
        if (toSummarize.isEmpty()) {
            return CompactionRewrite.failure("没有可压缩的早期消息。");
        }

        List<ExternalizedToolResultRef> refs = externalizedRefs(request.messages());
        Path sessionLogPath = request.store().writeSessionLog(new SessionLogSnapshot(request.messages(), refs));
        List<ConversationMessageSnapshot> summaryInput = List.copyOf(toSummarize);
        SummaryModelResult summaryResult = summarizeWithRetry(request, summaryInput, sessionLogPath);
        if (!summaryResult.success()) {
            return CompactionRewrite.failure(summaryResult.failureReason());
        }

        RestoredFileContextBuilder.RestoredFileContext restoredFiles = restoredFileContextBuilder.build(
                request.recentFileAccessTracker().recentFiles(request.contextConfig().restoredFileLimit()),
                request.contextConfig()
        );
        String skillDefinitions = skillDefinitions(request);
        String summaryMessage = buildSummaryMessage(
                ensureRequiredSections(summaryResult.finalSummary(), summaryInput),
                sessionLogPath,
                restoredFiles.content(),
                skillDefinitions
        );
        ConversationMessageSnapshot summarySnapshot = new ConversationMessageSnapshot(
                UUID.randomUUID().toString(),
                MessageRole.USER,
                MessageStatus.COMPLETE,
                Instant.now(),
                TokenUsage.unknown(),
                summaryMessage,
                List.of(new ContentBlock.Text(summaryMessage)),
                ConversationMessageMetadata.empty().asContextSummary(),
                null
        );
        List<ConversationMessageSnapshot> rewritten = new ArrayList<>();
        rewritten.add(summarySnapshot);
        rewritten.addAll(retained);
        request.store().writeCompactionMetadata(new CompactionMetadata(
                request.trigger(),
                Instant.now(),
                toSummarize.size(),
                retained.size(),
                refs.size(),
                restoredFiles.restoredFiles(),
                sessionLogPath
        ));
        return new CompactionRewrite(
                true,
                rewritten,
                toSummarize.size(),
                retained.size(),
                restoredFiles.restoredFiles(),
                sessionLogPath,
                null
        );
    }

    RetentionSplit splitForRetention(List<ConversationMessageSnapshot> messages, com.lunacode.config.ContextConfig config) {
        int start = messages.size();
        long tokens = 0;
        int count = 0;
        while (start > 0 && (tokens < config.recentTokenBudget() || count < config.minimumRecentMessages())) {
            start--;
            tokens += ContextText.estimateTokens(messages.get(start).content());
            tokens += ContextText.estimateTokens(blockText(messages.get(start)));
            count++;
        }
        start = expandToolBoundary(messages, start);
        return new RetentionSplit(
                List.copyOf(messages.subList(0, start)),
                List.copyOf(messages.subList(start, messages.size()))
        );
    }

    private int expandToolBoundary(List<ConversationMessageSnapshot> messages, int start) {
        int expanded = start;
        boolean changed;
        do {
            changed = false;
            if (expanded <= 0 || expanded >= messages.size()) {
                continue;
            }
            Set<String> retainedToolResults = new HashSet<>();
            for (int i = expanded; i < messages.size(); i++) {
                for (ContentBlock block : messages.get(i).blocks()) {
                    if (block instanceof ContentBlock.ToolResultBlock result) {
                        retainedToolResults.add(result.toolUseId());
                    }
                }
            }
            ConversationMessageSnapshot previous = messages.get(expanded - 1);
            for (ContentBlock block : previous.blocks()) {
                if (block instanceof ContentBlock.ToolUseBlock toolUse && retainedToolResults.contains(toolUse.id())) {
                    expanded--;
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return expanded;
    }

    private SummaryModelResult summarizeWithRetry(
            HistoryCompactionRequest request,
            List<ConversationMessageSnapshot> initialInput,
            Path sessionLogPath
    ) {
        List<ConversationMessageSnapshot> input = initialInput;
        int failures = 0;
        while (!input.isEmpty()) {
            String prompt = promptBuilder.build(input, sessionLogPath);
            SummaryModelResult result = request.summaryModelClient().summarize(new SummaryModelRequest(
                    request.provider(),
                    request.providerConfig(),
                    prompt
            ));
            if (result.success()) {
                return result;
            }
            if (result.failureType() != SummaryModelResult.FailureType.PROMPT_TOO_LONG) {
                return result;
            }
            failures++;
            input = retryPolicy.dropOldestForRetry(input, failures, request.contextConfig());
        }
        return SummaryModelResult.failure(SummaryModelResult.FailureType.PROMPT_TOO_LONG, "摘要请求过大，丢弃旧消息后仍无法生成摘要。");
    }

    private String buildSummaryMessage(String finalSummary, Path sessionLogPath, String restoredFiles, String skillDefinitions) {
        return """
                [上下文压缩摘要]
                %s

                [完整会话记录]
                路径: %s
                如果需要压缩前的具体代码片段、报错日志或工具结果细节，必须读取该记录或重新读取项目文件，不得根据摘要脑补。

                [压缩边界提示]
                本次会话延续自之前对话。早期历史已压缩为上方摘要，近期消息已在本条消息之后原样保留。

                [恢复文件快照]
                %s

                [已使用 Skill 定义]
                %s
                """.formatted(
                finalSummary == null ? "" : finalSummary,
                sessionLogPath,
                restoredFiles == null || restoredFiles.isBlank() ? "无恢复文件。" : restoredFiles,
                skillDefinitions == null || skillDefinitions.isBlank() ? "无已登记 Skill。" : skillDefinitions
        );
    }

    private String ensureRequiredSections(String summary, List<ConversationMessageSnapshot> input) {
        String safe = summary == null ? "" : summary.strip();
        String[] sections = {
                "主要请求和意图",
                "关键技术概念",
                "文件和代码段",
                "错误和修复",
                "问题解决过程",
                "所有用户消息",
                "待办任务",
                "当前工作",
                "可能的下一步"
        };
        StringBuilder prefix = new StringBuilder();
        for (String section : sections) {
            if (!safe.contains(section)) {
                prefix.append("## ").append(section).append('\n');
                if ("所有用户消息".equals(section)) {
                    for (ConversationMessageSnapshot message : input) {
                        if (message.role() == MessageRole.USER && !message.content().isBlank()) {
                            prefix.append("- ").append(message.content()).append('\n');
                        }
                    }
                } else {
                    prefix.append("未在模型摘要中单独提供。\n");
                }
                prefix.append('\n');
            }
        }
        return prefix + safe;
    }

    private String skillDefinitions(HistoryCompactionRequest request) {
        StringBuilder out = new StringBuilder();
        for (UsedSkillDefinition definition : request.usedSkillRegistry().recentDefinitions(
                request.contextConfig().skillDefinitionTokenBudget(),
                new ApproximateContextTokenEstimator()
        )) {
            out.append("\n--- skill: ").append(definition.name()).append(" ---\n");
            out.append(definition.definition()).append('\n');
        }
        return out.toString();
    }

    private List<ExternalizedToolResultRef> externalizedRefs(List<ConversationMessageSnapshot> messages) {
        List<ExternalizedToolResultRef> refs = new ArrayList<>();
        for (ConversationMessageSnapshot message : messages) {
            refs.addAll(message.metadata().externalizedToolResults());
        }
        return refs;
    }

    private String blockText(ConversationMessageSnapshot message) {
        StringBuilder out = new StringBuilder();
        for (ContentBlock block : message.blocks()) {
            if (block instanceof ContentBlock.Text text) {
                out.append(text.text());
            } else if (block instanceof ContentBlock.ToolResultBlock result) {
                out.append(result.content());
            } else if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                out.append(toolUse.name()).append(toolUse.input());
            }
        }
        return out.toString();
    }

    record RetentionSplit(
            List<ConversationMessageSnapshot> toSummarize,
            List<ConversationMessageSnapshot> retained
    ) {}
}
