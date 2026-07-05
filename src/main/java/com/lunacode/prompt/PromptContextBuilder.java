package com.lunacode.prompt;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.lunacode.conversation.ApiMessage;
import com.lunacode.instructions.DefaultProjectInstructionLoader;
import com.lunacode.instructions.ProjectInstructionLoader;
import com.lunacode.memory.MemoryContextLoader;
import com.lunacode.memory.MemoryIndexSnapshot;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.skill.SkillPromptContextLoader;
import com.lunacode.tool.ToolDeclarationSet;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class PromptContextBuilder {
    private final StaticSystemPromptBuilder staticPromptBuilder;
    private final EnvironmentContextCollector environmentCollector;
    private final MessageChannelBuilder messageChannelBuilder;
    private final ProjectInstructionLoader projectInstructionLoader;
    private final MemoryContextLoader memoryContextLoader;
    private final SkillPromptContextLoader skillPromptContextLoader;

    public PromptContextBuilder() {
        this(new StaticSystemPromptBuilder(), new EnvironmentContextCollector(), new MessageChannelBuilder(), new DefaultProjectInstructionLoader(), null, null);
    }

    public PromptContextBuilder(
            StaticSystemPromptBuilder staticPromptBuilder,
            EnvironmentContextCollector environmentCollector,
            MessageChannelBuilder messageChannelBuilder
    ) {
        this(staticPromptBuilder, environmentCollector, messageChannelBuilder, new DefaultProjectInstructionLoader(), null, null);
    }

    public PromptContextBuilder(
            StaticSystemPromptBuilder staticPromptBuilder,
            EnvironmentContextCollector environmentCollector,
            MessageChannelBuilder messageChannelBuilder,
            ProjectInstructionLoader projectInstructionLoader
    ) {
        this(staticPromptBuilder, environmentCollector, messageChannelBuilder, projectInstructionLoader, null, null);
    }

    public PromptContextBuilder(
            StaticSystemPromptBuilder staticPromptBuilder,
            EnvironmentContextCollector environmentCollector,
            MessageChannelBuilder messageChannelBuilder,
            ProjectInstructionLoader projectInstructionLoader,
            MemoryContextLoader memoryContextLoader
    ) {
        this(staticPromptBuilder, environmentCollector, messageChannelBuilder, projectInstructionLoader, memoryContextLoader, null);
    }

    public PromptContextBuilder(
            StaticSystemPromptBuilder staticPromptBuilder,
            EnvironmentContextCollector environmentCollector,
            MessageChannelBuilder messageChannelBuilder,
            ProjectInstructionLoader projectInstructionLoader,
            MemoryContextLoader memoryContextLoader,
            SkillPromptContextLoader skillPromptContextLoader
    ) {
        this.staticPromptBuilder = staticPromptBuilder;
        this.environmentCollector = environmentCollector;
        this.messageChannelBuilder = messageChannelBuilder;
        this.projectInstructionLoader = projectInstructionLoader;
        this.memoryContextLoader = memoryContextLoader;
        this.skillPromptContextLoader = skillPromptContextLoader;
    }

    public PromptBundle build(AgentRunConfig config, int turnIndex, List<ApiMessage> history, ArrayNode tools) {
        return build(config, turnIndex, history, new ToolDeclarationSet(tools, List.of()));
    }

    public PromptBundle build(AgentRunConfig config, int turnIndex, List<ApiMessage> history, ToolDeclarationSet tools) {
        StaticSystemPrompt staticPrompt = staticPromptBuilder.build();
        EnvironmentContext environment = environmentCollector.collect(config);
        ModeInjectionState modeState = new ModeInjectionState(config.mode(), turnIndex, config.planFile(), 3);
        Optional<ProjectInstructionContext> projectInstructions = loadProjectInstructions(config);
        Optional<MemoryContext> memory = loadMemory();
        SkillPromptContext skillContext = mergeSkillContexts(loadSkillContext(), config == null ? null : config.skillPromptContext());
        MessageChannel messages = messageChannelBuilder.build(
                config,
                modeState,
                history,
                tools.deferredTools(),
                projectInstructions,
                memory,
                skillContext
        );
        return new PromptBundle(
                new SystemChannel(staticPrompt, environment),
                tools.visibleTools(),
                messages,
                PromptCachePolicy.enabled()
        );
    }

    private Optional<ProjectInstructionContext> loadProjectInstructions(AgentRunConfig config) {
        if (projectInstructionLoader == null || config == null) {
            return Optional.empty();
        }
        Path workDir = config.workDir();
        try {
            com.lunacode.instructions.ProjectInstructionContext loaded = projectInstructionLoader.load(workDir, Path.of(System.getProperty("user.home")));
            String rendered = loaded.render();
            if (rendered.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ProjectInstructionContext(workDir, rendered));
        } catch (RuntimeException e) {
            String warning = "<!-- 项目指令加载失败，已跳过: " + e.getMessage() + " -->";
            return Optional.of(new ProjectInstructionContext(workDir, warning));
        }
    }

    private Optional<MemoryContext> loadMemory() {
        if (memoryContextLoader == null) {
            return Optional.empty();
        }
        try {
            MemoryIndexSnapshot snapshot = memoryContextLoader.loadForPrompt();
            if (snapshot == null || snapshot.mergedContent().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new MemoryContext(List.of(), List.of(snapshot.mergedContent())));
        } catch (RuntimeException e) {
            return Optional.of(new MemoryContext(List.of(), List.of("<!-- 记忆索引加载失败，已跳过: " + e.getMessage() + " -->")));
        }
    }

    private SkillPromptContext loadSkillContext() {
        if (skillPromptContextLoader == null) {
            return SkillPromptContext.empty();
        }
        try {
            SkillPromptContext context = skillPromptContextLoader.loadSummaries();
            return context == null ? SkillPromptContext.empty() : context;
        } catch (RuntimeException e) {
            return SkillPromptContext.empty();
        }
    }
    private SkillPromptContext mergeSkillContexts(SkillPromptContext summaries, SkillPromptContext invocationContext) {
        SkillPromptContext base = summaries == null ? SkillPromptContext.empty() : summaries;
        SkillPromptContext override = invocationContext == null ? SkillPromptContext.empty() : invocationContext;
        return new SkillPromptContext(
                base.summaries(),
                override.loadedSkill().or(base::loadedSkill)
        );
    }}
