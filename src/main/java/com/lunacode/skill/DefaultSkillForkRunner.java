package com.lunacode.skill;

import com.lunacode.agent.AgentLoop;
import com.lunacode.agent.AgentRequest;
import com.lunacode.conversation.ContentBlock;
import com.lunacode.conversation.ConversationManager;
import com.lunacode.conversation.ConversationMessageSnapshot;
import com.lunacode.conversation.DefaultConversationManager;
import com.lunacode.conversation.InternalMessage;
import com.lunacode.conversation.MessageRole;
import com.lunacode.conversation.MessageStatus;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class DefaultSkillForkRunner implements SkillForkRunner {
    private final SkillForkContextBuilder contextBuilder;
    private final Function<ConversationManager, AgentLoop> agentLoopFactory;

    public DefaultSkillForkRunner(Function<ConversationManager, AgentLoop> agentLoopFactory) {
        this(new SkillForkContextBuilder(), agentLoopFactory);
    }

    public DefaultSkillForkRunner(
            SkillForkContextBuilder contextBuilder,
            Function<ConversationManager, AgentLoop> agentLoopFactory
    ) {
        this.contextBuilder = contextBuilder == null ? new SkillForkContextBuilder() : contextBuilder;
        this.agentLoopFactory = Objects.requireNonNull(agentLoopFactory, "agentLoopFactory");
    }

    @Override
    public SkillForkResult runFork(
            SkillInvocationPlan plan,
            AgentRunConfig parentConfig,
            ConversationManager parentConversation,
            CancellationToken token
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(parentConfig, "parentConfig");

        DefaultConversationManager childConversation = new DefaultConversationManager();
        seedChildConversation(childConversation, contextBuilder.build(plan.definition().context(), parentConversation));

        LoadedSkillContext loadedSkill = new LoadedSkillContext(
                plan.definition().name(),
                plan.renderedPrompt(),
                plan.definition().resourceRoot()
        );
        AgentRunConfig childConfig = parentConfig
                .withToolAccessPolicy(plan.toolAccessPolicy())
                .withModelOverride(plan.modelOverride())
                .withSkillPromptContext(new SkillPromptContext(List.of(), Optional.of(loadedSkill)));

        String visibleRequest = "/" + plan.definition().name();
        AgentLoop childLoop = agentLoopFactory.apply(childConversation);
        childLoop.run(
                new AgentRequest(visibleRequest, plan.renderedPrompt(), childConfig),
                event -> {},
                token == null ? new CancellationToken() : token
        );

        return new SkillForkResult(
                plan.definition().name(),
                visibleRequest,
                summarizeChildConversation(childConversation),
                List.of(),
                List.of()
        );
    }

    private void seedChildConversation(DefaultConversationManager childConversation, List<ConversationMessageSnapshot> seeds) {
        for (ConversationMessageSnapshot seed : seeds) {
            if (seed.role() == MessageRole.USER) {
                childConversation.addMessage(MessageRole.USER, seed.content());
            } else if (seed.role() == MessageRole.ASSISTANT) {
                List<ContentBlock> blocks = seed.blocks().isEmpty()
                        ? List.of(new ContentBlock.Text(seed.content()))
                        : seed.blocks();
                childConversation.addAssistantMessage(blocks);
            }
        }
    }

    private String summarizeChildConversation(ConversationManager childConversation) {
        List<InternalMessage> messages = childConversation.snapshot();
        for (int i = messages.size() - 1; i >= 0; i--) {
            InternalMessage message = messages.get(i);
            if (message.role() == MessageRole.ASSISTANT
                    && message.status() == MessageStatus.COMPLETE
                    && !message.content().isBlank()) {
                return message.content().strip();
            }
        }
        return "fork Skill 已完成，但没有生成可回流的 assistant 摘要。";
    }
}
