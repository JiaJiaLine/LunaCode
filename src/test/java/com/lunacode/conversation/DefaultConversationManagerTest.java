package com.lunacode.conversation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DefaultConversationManagerTest {
    @Test
    void addMessageReturnsUniqueIdsAndSnapshot() {
        ConversationManager manager = new DefaultConversationManager();

        String first = manager.addMessage(MessageRole.USER, "你好");
        String second = manager.addMessage(MessageRole.USER, "继续");

        assertNotEquals(first, second);
        List<InternalMessage> snapshot = manager.snapshot();
        assertEquals(2, snapshot.size());
        assertEquals(MessageStatus.COMPLETE, snapshot.get(0).status());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.get(0)));
    }

    @Test
    void streamingAssistantCanAppendCompleteAndFail() {
        ConversationManager manager = new DefaultConversationManager();
        String assistantId = manager.addStreamingAssistantMessage();

        manager.appendContent(assistantId, "月");
        manager.appendContent(assistantId, "亮");
        manager.completeMessage(assistantId, new TokenUsage(3, 5, 8));

        InternalMessage complete = manager.snapshot().get(0);
        assertEquals("月亮", complete.content());
        assertEquals(MessageStatus.COMPLETE, complete.status());
        assertEquals(8, complete.usage().totalTokens());

        manager.failMessage(assistantId, "网络错误");
        InternalMessage failed = manager.snapshot().get(0);
        assertEquals(MessageStatus.ERROR, failed.status());
        assertEquals("网络错误", failed.errorSummary());
    }

    @Test
    void appendRejectsNonStreamingAssistant() {
        ConversationManager manager = new DefaultConversationManager();
        String userId = manager.addMessage(MessageRole.USER, "你好");

        assertThrows(IllegalStateException.class, () -> manager.appendContent(userId, "不该追加"));
    }

    @Test
    void toApiFormatFiltersAndMergesMessages() {
        ConversationManager manager = new DefaultConversationManager();
        manager.addMessage(MessageRole.SYSTEM, "系统提示");
        manager.addMessage(MessageRole.ASSISTANT, "开头 assistant 应丢弃");
        manager.addMessage(MessageRole.USER, "第一句");
        manager.addMessage(MessageRole.TOOL, "工具结果");
        manager.addMessage(MessageRole.USER, "第二句");
        String failed = manager.addMessage(MessageRole.ASSISTANT, "失败内容");
        manager.failMessage(failed, "失败");
        manager.addMessage(MessageRole.ASSISTANT, "回答一");
        manager.addMessage(MessageRole.ASSISTANT, "回答二");
        manager.addMessage(MessageRole.USER, "第三句");

        List<ApiMessage> apiMessages = manager.toAPIFormat();

        assertEquals(3, apiMessages.size());
        assertEquals(new ApiMessage("user", "第一句\n\n第二句"), apiMessages.get(0));
        assertEquals(new ApiMessage("assistant", "回答一\n\n回答二"), apiMessages.get(1));
        assertEquals(new ApiMessage("user", "第三句"), apiMessages.get(2));
    }

    @Test
    void toApiFormatSkipsBlankMessagesAndOnlyExposesRoleContent() {
        ConversationManager manager = new DefaultConversationManager();
        manager.addMessage(MessageRole.USER, " ");
        manager.addMessage(MessageRole.USER, "真实问题");

        List<ApiMessage> apiMessages = manager.toAPIFormat();

        assertEquals(List.of(new ApiMessage("user", "真实问题")), apiMessages);
    }

    @Test
    void supportsConcurrentAccess() throws Exception {
        DefaultConversationManager manager = new DefaultConversationManager();
        int threadCount = 8;
        int iterations = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int worker = i;
            executor.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        String id = manager.addMessage(MessageRole.USER, "u" + worker + "-" + j);
                        String assistant = manager.addStreamingAssistantMessage();
                        manager.appendContent(assistant, "a");
                        manager.completeMessage(assistant, new TokenUsage(null, j, null));
                        assertNotNull(id);
                        assertNotNull(manager.snapshot());
                        assertNotNull(manager.toAPIFormat());
                    }
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                }
            });
        }

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), () -> failures.toString());

        List<InternalMessage> snapshot = manager.snapshot();
        Set<String> ids = new HashSet<>();
        for (InternalMessage message : snapshot) {
            assertTrue(ids.add(message.id()), "重复 ID: " + message.id());
            assertNotNull(message.content());
        }
        assertEquals(threadCount * iterations * 2, snapshot.size());
    }
}