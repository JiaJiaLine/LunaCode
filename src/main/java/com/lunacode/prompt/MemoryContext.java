package com.lunacode.prompt;

import java.util.List;

public record MemoryContext(List<String> userPreferences, List<String> projectFacts) {
    public MemoryContext {
        userPreferences = userPreferences == null ? List.of() : List.copyOf(userPreferences);
        projectFacts = projectFacts == null ? List.of() : List.copyOf(projectFacts);
    }

    public boolean isEmpty() {
        return userPreferences.isEmpty() && projectFacts.isEmpty();
    }

    public String render() {
        if (isEmpty()) {
            return "";
        }
        if (userPreferences.isEmpty() && projectFacts.size() == 1 && projectFacts.get(0).contains("\n")) {
            return "[记忆索引]\n" + projectFacts.get(0).strip();
        }
        StringBuilder out = new StringBuilder("[记忆索引]\n");
        if (!userPreferences.isEmpty()) {
            out.append("## 用户偏好\n");
            userPreferences.forEach(item -> out.append("- ").append(item).append('\n'));
        }
        if (!projectFacts.isEmpty()) {
            out.append("## 项目知识\n");
            projectFacts.forEach(item -> out.append("- ").append(item).append('\n'));
        }
        return out.toString().strip();
    }
}
