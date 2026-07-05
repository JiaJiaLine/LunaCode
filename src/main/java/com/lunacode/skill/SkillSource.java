package com.lunacode.skill;

import java.nio.file.Path;
import java.util.List;

public interface SkillSource {
    List<SkillCandidate> discover(Path projectRoot, Path userHome);
}
