package com.lunacode.orchestrator;

import com.lunacode.agent.AgentRequest;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.prompt.PromptBundle;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.skill.DefaultSkillInvocationPlanner;
import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillCatalogSnapshot;
import com.lunacode.skill.SkillContextPolicy;
import com.lunacode.skill.SkillDefinition;
import com.lunacode.skill.SkillDiagnostic;
import com.lunacode.skill.SkillExecutionMode;
import com.lunacode.skill.SkillForkResult;
import com.lunacode.skill.SkillForkRunner;
import com.lunacode.skill.SkillInvocationPlan;
import com.lunacode.skill.SkillOrigin;
import com.lunacode.skill.SkillSourceKind;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillOrchestratorTest {
    @Test
    void slashSkillRunsInlineWithRenderedPromptAndVisibleHistory() {
        DefaultConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(Stream.of(
                new StreamEvent.ContentDelta(0, "commit message"),
                new StreamEvent.MessageStop(TokenUsage.unknown())
        ));
        DefaultChatOrchestrator orchestrator = orchestrator(manager, provider);
        SkillCatalog catalog = catalog(skill("commit", SkillExecutionMode.INLINE, "body $ARGUMENTS"));
        orchestrator.configureSkills(catalog, new DefaultSkillInvocationPlanner(catalog), null);

        orchestrator.submitUserMessage("/commit security");

        assertEquals("/commit security", manager.snapshot().get(0).content());
        assertEquals("commit message", manager.snapshot().get(1).content());
        assertTrue(provider.bundle.messages().history().get(0).textContent().contains("body security"));
    }

    @Test
    void slashSkillRunsWithoutArguments() {
        DefaultConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(Stream.of(
                new StreamEvent.ContentDelta(0, "ok"),
                new StreamEvent.MessageStop(TokenUsage.unknown())
        ));
        DefaultChatOrchestrator orchestrator = orchestrator(manager, provider);
        SkillCatalog catalog = catalog(skill("commit", SkillExecutionMode.INLINE, "body [$ARGUMENTS]"));
        orchestrator.configureSkills(catalog, new DefaultSkillInvocationPlanner(catalog), null);

        orchestrator.submitUserMessage("/commit");

        assertTrue(provider.bundle.messages().history().get(0).textContent().contains("body []"));
    }

    @Test
    void forkSkillWritesOnlySummaryBackToMainHistory() {
        DefaultConversationManager manager = new DefaultConversationManager();
        DefaultChatOrchestrator orchestrator = orchestrator(manager, new CapturingProvider(Stream.empty()));
        SkillCatalog catalog = catalog(skill("audit", SkillExecutionMode.FORK, "fork prompt"));
        orchestrator.configureSkills(catalog, new DefaultSkillInvocationPlanner(catalog), new FakeForkRunner());

        orchestrator.submitUserMessage("/audit api");

        assertEquals(2, manager.snapshot().size());
        assertEquals(MessageRole.USER, manager.snapshot().get(0).role());
        assertEquals(MessageRole.ASSISTANT, manager.snapshot().get(1).role());
        assertTrue(manager.snapshot().get(1).content().contains("fork summary"));
        assertFalse(manager.snapshot().get(1).content().contains("child detail"));
    }

    private DefaultChatOrchestrator orchestrator(ConversationManager manager, ChatProvider provider) {
        ToolExecutor executor = use -> ToolResult.success("tool", Map.of());
        return new DefaultChatOrchestrator(
                manager,
                provider,
                config(),
                new DefaultToolRegistry(),
                executor,
                () -> {},
                new DirectExecutorService()
        );
    }

    private ProviderConfig config() {
        return new ProviderConfig("openai", "gpt-test", URI.create("https://api.openai.com"), "secret", ThinkingConfig.disabled());
    }

    private SkillDefinition skill(String name, SkillExecutionMode mode, String body) {
        return new SkillDefinition(
                name,
                name + " helper",
                mode,
                SkillContextPolicy.FULL,
                Optional.empty(),
                List.of(),
                body,
                new SkillOrigin(SkillSourceKind.PROJECT, "test-" + name, Optional.empty(), 300),
                Optional.empty()
        );
    }

    private SkillCatalog catalog(SkillDefinition... definitions) {
        return new SkillCatalog() {
            @Override
            public SkillCatalogSnapshot snapshot() {
                return new SkillCatalogSnapshot(List.of(definitions).stream().map(SkillDefinition::summary).toList(), List.of());
            }

            @Override
            public Optional<SkillDefinition> loadForExecution(String name) {
                return List.of(definitions).stream()
                        .filter(definition -> definition.name().equals(name))
                        .findFirst();
            }

            @Override
            public List<SkillDiagnostic> diagnostics() {
                return List.of();
            }
        };
    }

    private static final class CapturingProvider implements ChatProvider {
        private final Stream<StreamEvent> events;
        private PromptBundle bundle;

        private CapturingProvider(Stream<StreamEvent> events) {
            this.events = events;
        }

        @Override
        public Stream<StreamEvent> streamChat(List<ApiMessage> messages, ProviderConfig config) {
            return events;
        }

        @Override
        public Stream<StreamEvent> streamChat(PromptBundle promptBundle, ProviderConfig config) {
            this.bundle = promptBundle;
            return events;
        }
    }

    private static final class FakeForkRunner implements SkillForkRunner {
        @Override
        public SkillForkResult runFork(
                SkillInvocationPlan plan,
                AgentRunConfig parentConfig,
                ConversationManager parentConversation,
                CancellationToken token
        ) {
            return new SkillForkResult(plan.definition().name(), "/audit api", "fork summary", List.of(), List.of());
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
