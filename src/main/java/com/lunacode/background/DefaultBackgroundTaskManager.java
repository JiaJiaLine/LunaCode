package com.lunacode.background;

import com.lunacode.subagent.SubAgentLaunchRequest;
import com.lunacode.subagent.SubAgentResult;
import com.lunacode.subagent.SubAgentRunHandle;
import com.lunacode.subagent.SubAgentRunnerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DefaultBackgroundTaskManager implements BackgroundTaskManager {
    private final SubAgentRunnerFactory runnerFactory;
    private final ExecutorService executor;
    private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final List<BackgroundTaskListener> listeners = new CopyOnWriteArrayList<>();

    public DefaultBackgroundTaskManager(SubAgentRunnerFactory runnerFactory) {
        this(runnerFactory, Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "lunacode-background-agent");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public DefaultBackgroundTaskManager(SubAgentRunnerFactory runnerFactory, ExecutorService executor) {
        this.runnerFactory = runnerFactory;
        this.executor = executor == null ? Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "lunacode-background-agent");
            thread.setDaemon(true);
            return thread;
        }) : executor;
    }

    @Override
    public String launch(SubAgentLaunchRequest request) {
        if (runnerFactory == null) {
            throw new IllegalStateException("后台任务管理器未配置子 Agent 运行器");
        }
        SubAgentRunHandle handle = runnerFactory.start(request);
        return track(handle, request == null ? "" : request.task());
    }

    @Override
    public String adoptRunning(SubAgentRunHandle handle, String task) {
        return track(handle, task);
    }

    @Override
    public Optional<BackgroundTaskSnapshot> get(String taskId) {
        BackgroundTask task = tasks.get(taskId);
        return task == null ? Optional.empty() : Optional.of(task.snapshot());
    }

    @Override
    public List<BackgroundTaskSnapshot> list() {
        return tasks.values().stream()
                .map(BackgroundTask::snapshot)
                .sorted(Comparator.comparing(BackgroundTaskSnapshot::startTime))
                .toList();
    }

    @Override
    public void addListener(BackgroundTaskListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private String track(SubAgentRunHandle handle, String taskText) {
        if (handle == null) {
            throw new IllegalArgumentException("子 Agent handle 不能为空");
        }
        String id = "bg-" + UUID.randomUUID();
        BackgroundTask task = new BackgroundTask(id, handle, taskText);
        tasks.put(id, task);
        handle.markAdoptedByBackground(id);
        executor.submit(() -> waitForCompletion(task, handle));
        return id;
    }

    private void waitForCompletion(BackgroundTask task, SubAgentRunHandle handle) {
        try {
            SubAgentResult result = handle.completion().join();
            if (result.failureReason().isPresent()) {
                task.fail(result.failureReason().orElse("子 Agent 执行失败"));
            } else {
                task.complete(result.fullResult());
            }
        } catch (RuntimeException e) {
            task.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            notifyListeners(task.id());
        }
    }

    private void notifyListeners(String taskId) {
        List<BackgroundTaskListener> copy = new ArrayList<>(listeners);
        for (BackgroundTaskListener listener : copy) {
            try {
                listener.onTaskFinished(taskId);
            } catch (RuntimeException ignored) {
                // 单个监听器失败不能影响后台任务生命周期。
            }
        }
    }
}
