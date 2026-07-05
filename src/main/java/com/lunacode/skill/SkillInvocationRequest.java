package com.lunacode.skill;

public record SkillInvocationRequest(
        String name,
        String rawArguments,
        SkillInvocationTrigger trigger
) {
    public SkillInvocationRequest {
        name = name == null ? "" : name.strip();
        rawArguments = rawArguments == null ? "" : rawArguments;
        trigger = trigger == null ? SkillInvocationTrigger.SLASH : trigger;
    }
}
