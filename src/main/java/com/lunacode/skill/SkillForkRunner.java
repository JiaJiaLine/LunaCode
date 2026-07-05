package com.lunacode.skill;

import com.lunacode.conversation.ConversationManager;
import com.lunacode.runtime.AgentRunConfig;
import com.lunacode.runtime.CancellationToken;

public interface SkillForkRunner {
    SkillForkResult runFork(
            SkillInvocationPlan plan,
            AgentRunConfig parentConfig,
            ConversationManager parentConversation,
            CancellationToken token
    );
}
