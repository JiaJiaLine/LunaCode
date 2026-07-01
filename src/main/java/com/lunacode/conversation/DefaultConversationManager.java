package com.lunacode.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DefaultConversationManager implements ConversationManager, ConversationCompactionAccess {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<MutableMessage> messages = new ArrayList<>();
    private final Clock clock;

    public DefaultConversationManager() {
        this(Clock.systemUTC());
    }

    DefaultConversationManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String addMessage(MessageRole role, String content) {
        Objects.requireNonNull(role, "role");
        lock.writeLock().lock();
        try {
            String id = newId();
            MessageStatus status = role == MessageRole.ASSISTANT ? MessageStatus.STREAMING : MessageStatus.COMPLETE;
            messages.add(new MutableMessage(id, role, status, Instant.now(clock), TokenUsage.unknown(), safe(content), blocksFor(role, content), ConversationMessageMetadata.empty(), null));
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String addAssistantMessage(List<ContentBlock> blocks) {
        lock.writeLock().lock();
        try {
            String id = newId();
            List<ContentBlock> safeBlocks = blocks == null ? List.of() : List.copyOf(blocks);
            messages.add(new MutableMessage(id, MessageRole.ASSISTANT, MessageStatus.COMPLETE, Instant.now(clock), TokenUsage.unknown(), textOf(safeBlocks), new ArrayList<>(safeBlocks), ConversationMessageMetadata.empty(), null));
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String addUserToolResultMessage(List<ContentBlock.ToolResultBlock> results) {
        lock.writeLock().lock();
        try {
            List<ContentBlock> blocks = new ArrayList<>();
            if (results != null) {
                blocks.addAll(results);
            }
            String id = newId();
            messages.add(new MutableMessage(id, MessageRole.TOOL, MessageStatus.COMPLETE, Instant.now(clock), TokenUsage.unknown(), textOf(blocks), blocks, ConversationMessageMetadata.empty(), null));
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String addStreamingAssistantMessage() {
        lock.writeLock().lock();
        try {
            String id = newId();
            messages.add(new MutableMessage(id, MessageRole.ASSISTANT, MessageStatus.STREAMING, Instant.now(clock), TokenUsage.unknown(), "", new ArrayList<>(), ConversationMessageMetadata.empty(), null));
            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void appendContent(String messageId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            if (message.role != MessageRole.ASSISTANT || message.status != MessageStatus.STREAMING) {
                throw new IllegalStateException("只能追加正在流式生成的 assistant 消息");
            }
            message.content += delta;
            if (!message.blocks.isEmpty() && message.blocks.get(message.blocks.size() - 1) instanceof ContentBlock.Text text) {
                message.blocks.set(message.blocks.size() - 1, new ContentBlock.Text(text.text() + delta));
            } else {
                message.blocks.add(new ContentBlock.Text(delta));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void appendToolUse(String messageId, ContentBlock.ToolUseBlock toolUse) {
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            if (message.role != MessageRole.ASSISTANT || message.status != MessageStatus.STREAMING) {
                throw new IllegalStateException("只能向正在流式生成的 assistant 消息追加工具调用");
            }
            message.blocks.add(toolUse);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void completeMessage(String messageId, TokenUsage usage) {
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            message.status = MessageStatus.COMPLETE;
            message.usage = message.usage.merge(usage);
            message.errorSummary = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void failMessage(String messageId, String errorSummary) {
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            message.status = MessageStatus.ERROR;
            message.errorSummary = safe(errorSummary);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<InternalMessage> snapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(messages.stream().map(MutableMessage::snapshot).toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ConversationMessageSnapshot> fullSnapshot() {
        lock.readLock().lock();
        try {
            return List.copyOf(messages.stream().map(MutableMessage::fullSnapshot).toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void replaceToolResultContent(
            String messageId,
            String toolUseId,
            ContentBlock.ToolResultBlock replacement,
            com.lunacode.context.ExternalizedToolResultRef ref
    ) {
        Objects.requireNonNull(replacement, "replacement");
        lock.writeLock().lock();
        try {
            MutableMessage message = find(messageId);
            boolean replaced = false;
            for (int i = 0; i < message.blocks.size(); i++) {
                ContentBlock block = message.blocks.get(i);
                if (block instanceof ContentBlock.ToolResultBlock toolResult
                        && Objects.equals(toolResult.toolUseId(), toolUseId)) {
                    message.blocks.set(i, replacement);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                throw new IllegalArgumentException("工具结果不存在: " + toolUseId);
            }
            message.content = textOf(message.blocks);
            message.metadata = message.metadata.withExternalizedToolResult(ref);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void rewriteForCompaction(List<ConversationMessageSnapshot> rewrittenMessages) {
        Objects.requireNonNull(rewrittenMessages, "rewrittenMessages");
        lock.writeLock().lock();
        try {
            messages.clear();
            for (ConversationMessageSnapshot snapshot : rewrittenMessages) {
                messages.add(MutableMessage.fromSnapshot(snapshot));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ApiMessage> toAPIFormat() {
        lock.readLock().lock();
        try {
            List<ApiMessage> result = new ArrayList<>();
            for (MutableMessage message : messages) {
                if (message.status == MessageStatus.ERROR) {
                    continue;
                }
                if (message.role == MessageRole.TOOL && normalizedBlocks(message).stream().noneMatch(ContentBlock.ToolResultBlock.class::isInstance)) {
                    continue;
                }
                if (message.role != MessageRole.USER && message.role != MessageRole.ASSISTANT && message.role != MessageRole.TOOL) {
                    continue;
                }
                if (normalizedBlocks(message).isEmpty() && message.content.isBlank()) {
                    continue;
                }
                String role = apiRole(message.role);
                if (result.isEmpty()) {
                    if (!role.equals("user")) {
                        continue;
                    }
                    result.add(new ApiMessage(role, normalizedBlocks(message)));
                    continue;
                }
                ApiMessage last = result.get(result.size() - 1);
                if (last.role().equals(role) && canMerge(last) && canMerge(message)) {
                    result.set(result.size() - 1, new ApiMessage(role, last.textContent() + "\n\n" + message.content));
                } else {
                    result.add(new ApiMessage(role, normalizedBlocks(message)));
                }
            }
            return List.copyOf(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    private MutableMessage find(String messageId) {
        return messages.stream()
                .filter(message -> message.id.equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("消息不存在: " + messageId));
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<ContentBlock> blocksFor(MessageRole role, String content) {
        String safeContent = safe(content);
        if ((role == MessageRole.USER || role == MessageRole.ASSISTANT) && !safeContent.isBlank()) {
            return new ArrayList<>(List.of(new ContentBlock.Text(safeContent)));
        }
        return new ArrayList<>();
    }

    private List<ContentBlock> normalizedBlocks(MutableMessage message) {
        if (!message.blocks.isEmpty()) {
            return List.copyOf(message.blocks);
        }
        return message.content.isBlank() ? List.of() : List.of(new ContentBlock.Text(message.content));
    }

    private boolean canMerge(ApiMessage message) {
        return message.content().stream().allMatch(ContentBlock.Text.class::isInstance);
    }

    private boolean canMerge(MutableMessage message) {
        return normalizedBlocks(message).stream().allMatch(ContentBlock.Text.class::isInstance);
    }

    private String textOf(List<ContentBlock> blocks) {
        StringBuilder result = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof ContentBlock.Text text) {
                result.append(text.text());
            } else if (block instanceof ContentBlock.ToolResultBlock toolResult) {
                if (!result.isEmpty()) {
                    result.append('\n');
                }
                result.append(toolResult.content());
            }
        }
        return result.toString();
    }

    private String apiRole(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "user";
            case SYSTEM -> throw new IllegalArgumentException("不支持的 API 角色: " + role);
        };
    }

    private static class MutableMessage {
        private final String id;
        private final MessageRole role;
        private MessageStatus status;
        private final Instant timestamp;
        private TokenUsage usage;
        private String content;
        private final List<ContentBlock> blocks;
        private ConversationMessageMetadata metadata;
        private String errorSummary;

        private MutableMessage(
                String id,
                MessageRole role,
                MessageStatus status,
                Instant timestamp,
                TokenUsage usage,
                String content,
                List<ContentBlock> blocks,
                ConversationMessageMetadata metadata,
                String errorSummary
        ) {
            this.id = id;
            this.role = role;
            this.status = status;
            this.timestamp = timestamp;
            this.usage = usage == null ? TokenUsage.unknown() : usage;
            this.content = content == null ? "" : content;
            this.blocks = blocks == null ? new ArrayList<>() : new ArrayList<>(blocks);
            this.metadata = metadata == null ? ConversationMessageMetadata.empty() : metadata;
            this.errorSummary = errorSummary;
        }

        private static MutableMessage fromSnapshot(ConversationMessageSnapshot snapshot) {
            return new MutableMessage(
                    snapshot.id(),
                    snapshot.role(),
                    snapshot.status(),
                    snapshot.timestamp(),
                    snapshot.usage(),
                    snapshot.content(),
                    snapshot.blocks(),
                    snapshot.metadata(),
                    snapshot.errorSummary()
            );
        }

        private InternalMessage snapshot() {
            return new InternalMessage(id, role, status, timestamp, usage, content, errorSummary);
        }

        private ConversationMessageSnapshot fullSnapshot() {
            return new ConversationMessageSnapshot(
                    id,
                    role,
                    status,
                    timestamp,
                    usage,
                    content,
                    List.copyOf(blocks),
                    metadata,
                    errorSummary
            );
        }
    }
}
