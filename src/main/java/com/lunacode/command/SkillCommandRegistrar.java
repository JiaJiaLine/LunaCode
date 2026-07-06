package com.lunacode.command;

import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillDefinition;
import com.lunacode.skill.SkillInvocationRequest;
import com.lunacode.skill.SkillInvocationTrigger;
import com.lunacode.skill.SkillSummary;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SkillCommandRegistrar {
    public void registerSkillCommands(SlashCommandRegistry registry, SkillCatalog catalog, CommandRuntime runtime) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(runtime, "runtime");
        registry.clearDynamicCommands();
        List<SkillSummary> summaries = catalog.snapshot().summaries();
        registry.registerDynamic(new SlashCommandDefinition(
                "/skill",
                List.of("/skills"),
                "列出或运行 Skill",
                "/skill [name] [arguments]",
                SlashCommandType.LOCAL,
                "[name] [arguments]",
                false,
                context -> handleSkillCommand(catalog, runtime, context.args())
        ));
        for (SkillSummary summary : summaries) {
            if (isReservedSkillCommand(summary.name())) {
                continue;
            }
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

    private void handleSkillCommand(SkillCatalog catalog, CommandRuntime runtime, String rawArgs) {
        String args = rawArgs == null ? "" : rawArgs.strip();
        if (args.isBlank()) {
            showSkillList(catalog.snapshot().summaries(), runtime);
            return;
        }

        ParsedSkillArgs parsed = parseArgs(args);
        Optional<SkillDefinition> definition = catalog.loadForExecution(parsed.name());
        if (definition.isEmpty()) {
            runtime.showError("未知 Skill: " + parsed.name() + "。输入 /skill 查看可用 Skill。");
            return;
        }
        runtime.submitSkillInvocation(new SkillInvocationRequest(
                definition.get().name(),
                parsed.arguments(),
                SkillInvocationTrigger.SLASH
        ));
    }

    private void showSkillList(List<SkillSummary> summaries, CommandRuntime runtime) {
        if (summaries.isEmpty()) {
            runtime.showInfo("当前没有可用 Skill。");
            return;
        }
        StringBuilder out = new StringBuilder("可用 Skill:");
        for (SkillSummary summary : summaries) {
            out.append('\n')
                    .append("- ")
                    .append(summary.name())
                    .append(" - ")
                    .append(summary.description());
        }
        out.append("\n\n用法: /skill <name> [arguments]");
        runtime.showInfo(out.toString());
    }

    private ParsedSkillArgs parseArgs(String args) {
        int split = firstWhitespace(args);
        String name = split < 0 ? args : args.substring(0, split);
        String rest = split < 0 ? "" : args.substring(split).strip();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return new ParsedSkillArgs(name, rest);
    }

    private int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isReservedSkillCommand(String name) {
        String normalized = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("skill") || normalized.equals("skills");
    }

    private record ParsedSkillArgs(String name, String arguments) {}
}
