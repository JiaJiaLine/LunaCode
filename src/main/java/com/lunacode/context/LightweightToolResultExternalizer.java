package com.lunacode.context;

import com.lunacode.config.ContextConfig;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.MessageRole;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LightweightToolResultExternalizer {
    private static final int PREVIEW_CHARS = 2_000;

    public LightweightCompactionResult externalizeOversizedResults(
            List<ConversationMessageSnapshot> messages,
            ContextConfig config,
            SessionContextStore store,
            ConversationCompactionAccess conversation
    ) {
        if (messages == null || messages.isEmpty()) {
            return LightweightCompactionResult.empty();
        }
        Map<String, String> toolNames = toolNamesByUseId(messages);
        List<ExternalizedToolResultRef> refs = new ArrayList<>();
        Set<String> externalizedKeys = new HashSet<>();

        for (ConversationMessageSnapshot message : messages) {
            if (message.role() != MessageRole.TOOL) {
                continue;
            }
            List<ToolResultCandidate> candidates = toolResultCandidates(message, toolNames);
            for (ToolResultCandidate candidate : candidates) {
                if (candidate.content().length() > config.singleToolResultCharLimit()) {
                    refs.add(externalize(candidate, config, store, conversation));
                    externalizedKeys.add(candidate.key());
                }
            }
        }

        for (ConversationMessageSnapshot message : messages) {
            if (message.role() != MessageRole.TOOL) {
                continue;
            }
            List<ToolResultCandidate> remaining = toolResultCandidates(message, toolNames).stream()
                    .filter(candidate -> !externalizedKeys.contains(candidate.key()))
                    .sorted(Comparator.comparingInt((ToolResultCandidate candidate) -> candidate.content().length()).reversed())
                    .toList();
            int totalChars = remaining.stream().mapToInt(candidate -> candidate.content().length()).sum();
            for (ToolResultCandidate candidate : remaining) {
                if (totalChars <= config.toolMessageCharLimit()) {
                    break;
                }
                refs.add(externalize(candidate, config, store, conversation));
                externalizedKeys.add(candidate.key());
                totalChars -= candidate.content().length();
            }
        }

        return new LightweightCompactionResult(refs.size(), refs);
    }

    private ExternalizedToolResultRef externalize(
            ToolResultCandidate candidate,
            ContextConfig config,
            SessionContextStore store,
            ConversationCompactionAccess conversation
    ) {
        Path path = store.writeToolResult(new ExternalizedToolResultPayload(
                candidate.messageId(),
                candidate.toolUseId(),
                candidate.toolName(),
                candidate.content(),
                candidate.error()
        ));
        String preview = preview(store, candidate.content());
        ExternalizedToolResultRef ref = new ExternalizedToolResultRef(
                candidate.messageId(),
                candidate.toolUseId(),
                candidate.toolName(),
                path,
                candidate.content().length(),
                preview.length(),
                candidate.error()
        );
        String replacement = """
                [工具结果已外置]
                工具: %s
                原始字符数: %d
                完整结果路径: %s
                重新读取提示: 如需完整工具结果，请用文件读取工具读取上面的路径。

                [预览]
                %s
                """.formatted(candidate.toolName(), candidate.content().length(), path, preview);
        conversation.replaceToolResultContent(
                candidate.messageId(),
                candidate.toolUseId(),
                new ContentBlock.ToolResultBlock(candidate.toolUseId(), replacement, candidate.error()),
                ref
        );
        return ref;
    }

    private String preview(SessionContextStore store, String content) {
        if (store instanceof ProjectSessionContextStore projectStore) {
            return projectStore.maskedPreview(content, PREVIEW_CHARS);
        }
        String safe = content == null ? "" : content;
        return safe.length() <= PREVIEW_CHARS ? safe : safe.substring(0, PREVIEW_CHARS);
    }

    private List<ToolResultCandidate> toolResultCandidates(ConversationMessageSnapshot message, Map<String, String> toolNames) {
        List<ToolResultCandidate> candidates = new ArrayList<>();
        for (ContentBlock block : message.blocks()) {
            if (block instanceof ContentBlock.ToolResultBlock result) {
                String toolName = toolNames.getOrDefault(result.toolUseId(), "UnknownTool");
                candidates.add(new ToolResultCandidate(
                        message.id(),
                        result.toolUseId(),
                        toolName,
                        result.content(),
                        result.isError()
                ));
            }
        }
        return candidates;
    }

    private Map<String, String> toolNamesByUseId(List<ConversationMessageSnapshot> messages) {
        Map<String, String> names = new HashMap<>();
        for (ConversationMessageSnapshot message : messages) {
            for (ContentBlock block : message.blocks()) {
                if (block instanceof ContentBlock.ToolUseBlock toolUse) {
                    names.put(toolUse.id(), toolUse.name());
                }
            }
        }
        return names;
    }

    private record ToolResultCandidate(
            String messageId,
            String toolUseId,
            String toolName,
            String content,
            boolean error
    ) {
        private String key() {
            return messageId + "\n" + toolUseId;
        }
    }
}
