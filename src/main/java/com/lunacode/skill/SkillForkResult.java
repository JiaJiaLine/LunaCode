package com.lunacode.skill;

import java.util.List;

public record SkillForkResult(
        String skillName,
        String userRequest,
        String summary,
        List<String> artifactPaths,
        List<String> nextSteps
) {
    public SkillForkResult {
        skillName = skillName == null ? "" : skillName.strip();
        userRequest = userRequest == null ? "" : userRequest;
        summary = summary == null ? "" : summary;
        artifactPaths = artifactPaths == null ? List.of() : List.copyOf(artifactPaths);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
    }
}
