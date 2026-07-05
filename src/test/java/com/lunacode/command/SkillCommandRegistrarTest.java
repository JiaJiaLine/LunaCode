package com.lunacode.command;

import com.lunacode.permission.PermissionMode;
import com.lunacode.runtime.AgentMode;
import com.lunacode.skill.SkillCatalog;
import com.lunacode.skill.SkillCatalogSnapshot;
import com.lunacode.skill.SkillDiagnostic;
import com.lunacode.skill.SkillExecutionMode;
import com.lunacode.skill.SkillInvocationRequest;
import com.lunacode.skill.SkillOrigin;
import com.lunacode.skill.SkillSummary;
import com.lunacode.skill.SkillSourceKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCommandRegistrarTest {
    @Test
    void registersSkillsForHelpCompletionAndDispatch() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        BuiltinSlashCommands.registerAll(registry);
        SkillCommandRegistrar registrar = new SkillCommandRegistrar();
        RecordingRuntime runtime = new RecordingRuntime();

        registrar.registerSkillCommands(registry, catalog(
                summary("commit", "commit helper"),
                summary("test", "test helper")
        ), runtime);

        assertTrue(registry.visibleCommands().stream().anyMatch(command -> command.name().equals("/commit")));
        assertTrue(registry.visibleNames().stream().anyMatch(name -> name.value().equals("/test")));

        new SlashCommandDispatcher(registry, new SlashCommandParser()).dispatch("/commit 重点关注安全", runtime);

        assertEquals("commit", runtime.lastRequest.orElseThrow().name());
        assertEquals("重点关注安全", runtime.lastRequest.orElseThrow().rawArguments());
    }

    @Test
    void keepsBuiltinReviewCommand() {
        SlashCommandRegistry registry = new SlashCommandRegistry();
        BuiltinSlashCommands.registerAll(registry);
        new SkillCommandRegistrar().registerSkillCommands(registry, catalog(summary("commit", "commit helper")), new RecordingRuntime());

        assertEquals("/review", registry.require("/review").name());
    }

    private SkillSummary summary(String name, String description) {
        return new SkillSummary(
                name,
                description,
                new SkillOrigin(SkillSourceKind.BUILTIN, "builtin-" + name, Optional.empty(), 100),
                SkillExecutionMode.INLINE
        );
    }

    private SkillCatalog catalog(SkillSummary... summaries) {
        return new SkillCatalog() {
            @Override
            public SkillCatalogSnapshot snapshot() {
                return new SkillCatalogSnapshot(List.of(summaries), List.of());
            }

            @Override
            public Optional<com.lunacode.skill.SkillDefinition> loadForExecution(String name) {
                return Optional.empty();
            }

            @Override
            public List<SkillDiagnostic> diagnostics() {
                return List.of();
            }
        };
    }

    private static final class RecordingRuntime implements CommandRuntime {
        private Optional<SkillInvocationRequest> lastRequest = Optional.empty();

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public boolean hasPendingUserAnswer() {
            return false;
        }

        @Override
        public boolean hasPendingPermissionAnswer() {
            return false;
        }

        @Override
        public boolean hasPendingDangerousModeConfirmation() {
            return false;
        }

        @Override
        public CommandRuntimeStatus runtimeStatus() {
            return new CommandRuntimeStatus(AgentMode.DEFAULT, PermissionMode.DEFAULT, "test", "model", 0, 0, "idle", "", null, "");
        }

        @Override
        public void showInfo(String message) {
        }

        @Override
        public void showWarning(String message) {
        }

        @Override
        public void showError(String message) {
        }

        @Override
        public void requestRender() {
        }

        @Override
        public void cancelCurrentRun() {
        }

        @Override
        public void clearVisibleScreen() {
        }

        @Override
        public void sendUserMessage(String message) {
        }

        @Override
        public void submitSkillInvocation(SkillInvocationRequest request) {
            lastRequest = Optional.ofNullable(request);
        }

        @Override
        public void compactContext() {
        }

        @Override
        public void enterPlanMode() {
        }

        @Override
        public void enterDefaultMode() {
        }

        @Override
        public void switchPermissionMode(PermissionMode mode) {
        }

        @Override
        public void requestDangerousPermissionMode(PermissionMode mode) {
        }

        @Override
        public void runSessionCommand(String rawInput) {
        }

        @Override
        public void runMemoryCommand(String rawInput) {
        }
    }
}
