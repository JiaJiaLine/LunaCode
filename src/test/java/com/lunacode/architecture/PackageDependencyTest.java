package com.lunacode.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageDependencyTest {
    @Test
    void providerToolAndPromptDoNotDependOnAgentPackage() throws Exception {
        Path sourceRoot = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "lunacode");
        List<String> violations = new ArrayList<>();
        for (String packageName : List.of("provider", "tool", "prompt")) {
            Path packageRoot = sourceRoot.resolve(packageName);
            try (Stream<Path> files = Files.walk(packageRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collectForbiddenImports(sourceRoot, path, violations));
            }
        }
        assertTrue(violations.isEmpty(), "禁止 provider/tool/prompt 依赖 agent 包:\n" + String.join("\n", violations));
    }

    private void collectForbiddenImports(Path sourceRoot, Path path, List<String> violations) {
        try {
            int lineNumber = 0;
            for (String line : Files.readAllLines(path)) {
                lineNumber++;
                if (line.startsWith("import com.lunacode.agent.")) {
                    violations.add(sourceRoot.relativize(path) + ":" + lineNumber + " -> " + line);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取源码失败: " + path, e);
        }
    }
}