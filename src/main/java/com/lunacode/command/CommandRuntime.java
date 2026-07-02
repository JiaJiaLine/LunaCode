package com.lunacode.command;

import com.lunacode.permission.PermissionMode;

public interface CommandRuntime {
    boolean isBusy();

    boolean hasPendingUserAnswer();

    boolean hasPendingPermissionAnswer();

    boolean hasPendingDangerousModeConfirmation();

    CommandRuntimeStatus runtimeStatus();

    void showInfo(String message);

    void showWarning(String message);

    void showError(String message);

    void requestRender();

    void cancelCurrentRun();

    void clearVisibleScreen();

    void sendUserMessage(String message);

    void compactContext();

    void enterPlanMode();

    void enterDefaultMode();

    void switchPermissionMode(PermissionMode mode);

    void requestDangerousPermissionMode(PermissionMode mode);

    void runSessionCommand(String rawInput);

    void runMemoryCommand(String rawInput);
}
