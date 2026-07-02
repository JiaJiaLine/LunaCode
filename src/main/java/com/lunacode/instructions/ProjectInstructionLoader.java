package com.lunacode.instructions;

import java.nio.file.Path;

public interface ProjectInstructionLoader {
    ProjectInstructionContext load(Path projectRoot, Path userHome);
}
