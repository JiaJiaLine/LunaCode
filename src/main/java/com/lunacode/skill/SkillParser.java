package com.lunacode.skill;

import java.nio.file.Path;

public interface SkillParser {
    SkillParseResult parseSingleFile(Path markdownFile, SkillOrigin origin);

    SkillParseResult parseDirectory(Path skillDirectory, SkillOrigin origin);

    SkillParseResult parseBuiltin(String resourceName, String content, SkillOrigin origin);
}
