package com.lunacode.command;

import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillInvocationRequest;
import com.lunacode.skill.SkillInvocationTrigger;
import com.lunacode.skill.SkillSummary;

import java.util.List;
import java.util.Objects;

public final class SkillCommandRegistrar {
    public void registerSkillCommands(SlashCommandRegistry registry, SkillCatalog catalog, CommandRuntime runtime) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(runtime, "runtime");
        registry.clearDynamicCommands();
        for (SkillSummary summary : catalog.snapshot().summaries()) {
            SlashCommandDefinition definition = new SlashCommandDefinition(
                    "/" + summary.name(),
                    List.of(),
                    summary.description(),
                    "/" + summary.name() + " [arguments]",
                    SlashCommandType.PROMPT,
                    "[arguments]",
                    false,
                    context -> runtime.submitSkillInvocation(new SkillInvocationRequest(
                            summary.name(),
                            context.args(),
                            SkillInvocationTrigger.SLASH
                    ))
            );
            registry.registerDynamic(definition);
        }
    }
}