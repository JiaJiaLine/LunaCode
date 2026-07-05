package com.lunacode.prompt;

import com.lunacode.skill.LoadedSkillContext;
import com.lunacode.skill.SkillPromptContext;
import com.lunacode.skill.SkillSummary;

public final class SkillPromptRenderer {
    public String render(SkillPromptContext context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        if (!context.summaries().isEmpty()) {
            out.append("## Available Skills\n");
            out.append("Only the following lightweight Skill summaries are loaded by default. ");
            out.append("When a full SOP is needed, call the LoadSkill tool with the Skill name.\n");
            for (SkillSummary summary : context.summaries()) {
                out.append("- /")
                        .append(summary.name())
                        .append(" (")
                        .append(summary.mode().name().toLowerCase(java.util.Locale.ROOT))
                        .append("): ")
                        .append(summary.description())
                        .append('\n');
            }
        }
        context.loadedSkill().ifPresent(loaded -> appendLoaded(out, loaded));
        return out.toString().strip();
    }

    private void appendLoaded(StringBuilder out, LoadedSkillContext loaded) {
        if (!out.isEmpty()) {
            out.append('\n').append('\n');
        }
        out.append("## Loaded Skill SOP: /").append(loaded.skillName()).append('\n');
        out.append(loaded.renderedPrompt()).append('\n');
        loaded.resourceRoot().ifPresent(root -> {
            out.append('\n');
            out.append("Resource root: ").append(root).append('\n');
            out.append("Do not preload directory resources. Read examples, templates, scripts, or references only when the SOP asks for them.\n");
        });
    }
}
