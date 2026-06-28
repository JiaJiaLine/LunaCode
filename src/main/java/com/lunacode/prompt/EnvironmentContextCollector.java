package com.lunacode.prompt;

import com.lunacode.runtime.AgentRunConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public final class EnvironmentContextCollector {
    public EnvironmentContext collect(AgentRunConfig config) {
        return new EnvironmentContext(
                config.workDir(),
                System.getProperty("os.name", "unknown"),
                Instant.now(config.clock()),
                collectGitStatus(config)
        );
    }

    private GitStatusSnapshot collectGitStatus(AgentRunConfig config) {
        try {
            String inside = runGit(config, "rev-parse", "--is-inside-work-tree");
            if (!"true".equalsIgnoreCase(inside.strip())) {
                return GitStatusSnapshot.unknown("not a git repository");
            }
            String branch = runGit(config, "branch", "--show-current").strip();
            String status = runGit(config, "status", "--porcelain");
            boolean dirty = !status.isBlank();
            String summary = dirty ? firstLine(status) : "clean";
            return new GitStatusSnapshot(true, branch.isBlank() ? "unknown" : branch, dirty, summary);
        } catch (Exception e) {
            return GitStatusSnapshot.unknown(e.getMessage());
        }
    }

    private String runGit(AgentRunConfig config, String... args) throws Exception {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = config.workDir().toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(2, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git status timeout");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String output = reader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(output.isBlank() ? "git command failed" : output);
            }
            return output;
        }
    }

    private String firstLine(String text) {
        int index = text.indexOf('\n');
        return index < 0 ? text.strip() : text.substring(0, index).strip();
    }
}
