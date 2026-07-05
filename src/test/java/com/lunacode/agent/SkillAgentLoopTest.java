package com.lunacode.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunacode.agent.event.AgentEvent;
import com.lunacode.agent.execution.AgentToolRunner;
import com.lunacode.agent.turn.AgentTurnRunner;
import com.lunacode.config.ProviderConfig;
import com.lunacode.config.ThinkingConfig;
import com.lunacode.context.ExternalizedToolResultRef;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationCompactionAccess;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.TokenUsage;
import com.lunacode.prompt.PromptBundle;
import com.lunacode.prompt.PromptContextBuilder;
import com.lunacode.provider.ChatProvider;
import com.lunacode.runtime.AgentMode;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;
import com.lunacode.skill.DefaultSkillInvocationPlanner;
import com.lunacode.skill.LoadedSkillContext;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.skill.ToolAccessPolicy;
import com.lunacode.stream.StreamEvent;
import com.lunacode.tool.DefaultToolPermissionGateway;
import com.lunacode.tool.DefaultToolRegistry;
import com.lunacode.tool.Tool;
import com.lunacode.tool.ToolBatchPlanner;
import com.lunacode.tool.ToolExecutionContext;
import com.lunacode.tool.ToolExecutor;
import com.lunacode.tool.ToolResult;
import com.lunacode.tool.ValidationError;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillAgentLoopTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void inlineSkillStoresVisibleMessageButModelReceivesRenderedPromptAndModelOverride() {
        DefaultConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(Stream.of(
                new StreamEvent.ContentDelta(0, "done"),
                new StreamEvent.MessageStop(TokenUsage.unknown())
        )));
        DefaultToolRegistry registry = new DefaultToolRegistry();
        DefaultAgentLoop loop = createLoop(manager, provider, registry, use -> ToolResult.success("unused", Map.of()));
        AgentRunConfig runConfig = runConfig()
                .withModelOverride(Optional.of("skill-model"))
                .withSkillPromptContext(loadedContext("commit", "Skill SOP with security"));

        loop.run(new AgentRequest("/commit security", "Skill SOP with security", runConfig), event -> {}, new CancellationToken());

        assertEquals("/commit security", manager.snapshot().get(0).content());
        assertEquals(MessageRole.ASSISTANT, manager.snapshot().get(1).role());
        assertTrue(provider.bundles.get(0).messages().history().get(0).textContent().contains("Skill SOP with security"));
        assertTrue(provider.bundles.get(0).messages().skillContext().loadedSkill().isPresent());
        assertEquals("skill-model", provider.configs.get(0).model());
    }

    @Test
    void toolWhitelistFiltersDeclarationsAndRejectsExecution() {
        DefaultConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(
                Stream.of(new StreamEvent.ToolUse("toolu_1", "WriteFile", mapper.createObjectNode()), new StreamEvent.MessageStop(TokenUsage.unknown())),
                Stream.of(new StreamEvent.ContentDelta(0, "done"), new StreamEvent.MessageStop(TokenUsage.unknown()))
        ));
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool("ReadFile"));
        registry.register(new StubTool("WriteFile"));
        registry.register(new StubTool(DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME));
        DefaultAgentLoop loop = createLoop(manager, provider, registry, use -> ToolResult.success("should not run", Map.of()));
        AgentRunConfig runConfig = runConfig()
                .withToolAccessPolicy(ToolAccessPolicy.restricted(
                        Set.of("ReadFile"),
                        Set.of(DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME)
                ));

        loop.run(new AgentRequest("run", runConfig), event -> {}, new CancellationToken());

        String declarations = provider.bundles.get(0).toolDeclarations().toString();
        assertTrue(declarations.contains("ReadFile"));
        assertTrue(declarations.contains(DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME));
        assertFalse(declarations.contains("WriteFile"));
        assertTrue(manager.snapshot().stream().anyMatch(message -> message.content().contains("不允许") || message.content().contains("涓嶅厑璁")));
    }

    @Test
    void loadSkillToolResultIsCleanedAfterRun() {
        DefaultConversationManager manager = new DefaultConversationManager();
        CapturingProvider provider = new CapturingProvider(List.of(
                Stream.of(new StreamEvent.ToolUse("toolu_1", DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME, mapper.createObjectNode()), new StreamEvent.MessageStop(TokenUsage.unknown())),
                Stream.of(new StreamEvent.ContentDelta(0, "done"), new StreamEvent.MessageStop(TokenUsage.unknown()))
        ));
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(new StubTool(DefaultSkillInvocationPlanner.LOAD_SKILL_TOOL_NAME));
        ToolExecutor executor = use -> ToolResult.success("full SOP should be removed", Map.of(
                "loadedSkill", true,
                "skillName", "commit"
        ));
        DefaultAgentLoop loop = createLoop(manager, provider, registry, executor);

        loop.run(new AgentRequest("load skill", runConfig()), event -> {}, new CancellationToken());

        String fullHistory = ((ConversationCompactionAccess) manager).fullSnapshot().toString();
        assertFalse(fullHistory.contains("full SOP should be removed"));
        assertTrue(fullHistory.contains("Skill /commit loaded"));
    }

    private DefaultAgentLoop createLoop(
            ConversationManager manager,
            CapturingProvider provider,
            DefaultToolRegistry registry,
            ToolExecutor executor
    ) {
        Path workspaceRoot = Path.of(".").toAbsolutePath().normalize();
        AgentToolRunner toolRunner = new AgentToolRunner(
                registry,
                executor,
                new ToolBatchPlanner(),
                new DefaultToolPermissionGateway(workspaceRoot)
        );
        return new DefaultAgentLoop(
                manager,
                providerConfig("default-model"),
                registry,
                toolRunner,
                new AgentTurnRunner(manager, provider),
                new LoopDecisionMaker(),
                new PromptContextBuilder()
        );
    }

    private AgentRunConfig runConfig() {
        return new AgentRunConfig(Path.of("."), AgentMode.DEFAULT, Path.of("plan.md"), 4, 3, Clock.systemUTC());
    }

    private SkillPromptContext loadedContext(String name, String prompt) {
        return new SkillPromptContext(
                List.of(),
                Optional.of(new LoadedSkillContext(name, prompt, Optional.empty()))
        );
    }

    private ProviderConfig providerConfig(String model) {
        return new ProviderConfig("anthropic", model, URI.create("https://api.anthropic.com"), "secret", ThinkingConfig.disabled());
    }

    private static final class CapturingProvider implements ChatProvider {
        private final List<Stream<StreamEvent>> streams;
        private final List<PromptBundle> bundles = new ArrayList<>();
        private final List<ProviderConfig> configs = new ArrayList<>();
        private int calls;

        private CapturingProvider(List<Stream<StreamEvent>> streams) {
            this.streams = streams;
        }

        @Override
        public Stream<StreamEvent> streamChat(List<com.lunacode.conversation.ApiMessage> messages, ProviderConfig config) {
            configs.add(config);
            return streams.get(calls++);
        }

        @Override
        public Stream<StreamEvent> streamChat(PromptBundle promptBundle, ProviderConfig config) {
            bundles.add(promptBundle);
            configs.add(config);
            return streams.get(calls++);
        }
    }

    private final class StubTool implements Tool {
        private final String name;

        private StubTool(String name) {
            this.name = name;
        }

        @Override public String name() { return name; }
        @Override public String description() { return name; }
        @Override public JsonNode inputSchema() { return mapper.createObjectNode().put("type", "object"); }
        @Override public ToolResult execute(ToolExecutionContext context, JsonNode input) { return ToolResult.success("ok", Map.of()); }
        @Override public boolean isReadOnly() { return true; }
        @Override public boolean isDestructive() { return false; }
        @Override public boolean isConcurrencySafe(JsonNode input) { return true; }
        @Override public String category() { return "test"; }
        @Override public ValidationError validateInput(JsonNode input) { return null; }
    }
}
