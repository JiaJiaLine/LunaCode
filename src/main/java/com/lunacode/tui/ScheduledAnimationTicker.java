package com.lunacode.tui;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用单个 daemon 线程驱动动画，不在空闲状态执行任务。
 */
public final class ScheduledAnimationTicker implements AnimationTicker {
    private static final Duration DEFAULT_PERIOD = Duration.ofMillis(100);

    private final ScheduledExecutorService scheduler;
    private final long periodMillis;
    private final boolean ownsScheduler;
    private ScheduledFuture<?> future;
    private boolean closed;

    public ScheduledAnimationTicker() {
        this(newScheduler(), DEFAULT_PERIOD, true);
    }

    public ScheduledAnimationTicker(Duration period) {
        this(newScheduler(), period, true);
    }

    ScheduledAnimationTicker(ScheduledExecutorService scheduler, Duration period) {
        this(scheduler, period, false);
    }

    private ScheduledAnimationTicker(
            ScheduledExecutorService scheduler,
            Duration period,
            boolean ownsScheduler
    ) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(period, "period");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive");
        }
        long millis = period.toMillis();
        if (millis <= 0) {
            throw new IllegalArgumentException("period must be at least one millisecond");
        }
        this.periodMillis = millis;
        this.ownsScheduler = ownsScheduler;
    }

    @Override
    public synchronized void start(Runnable tick) {
        Objects.requireNonNull(tick, "tick");
        if (closed) {
            throw new IllegalStateException("ticker is closed");
        }
        if (running()) {
            return;
        }
        future = scheduler.scheduleAtFixedRate(
                () -> runTick(tick),
                periodMillis,
                periodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public synchronized void stop() {
        if (future == null) {
            return;
        }
        future.cancel(false);
        future = null;
    }

    @Override
    public synchronized boolean running() {
        return !closed
                && future != null
                && !future.isCancelled()
                && !future.isDone();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        stop();
        closed = true;
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    private void runTick(Runnable tick) {
        synchronized (this) {
            if (!running()) {
                return;
            }
        }
        try {
            // 回调不能持有 ticker 锁：渲染线程会在持有 TUI 锁时调用 stop/running。
            tick.run();
        } catch (RuntimeException ignored) {
            // 渲染异常由 TUI 生命周期处理，不能让调度线程永久停止。
        }
    }

    private static ScheduledExecutorService newScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lunacode-tui-animation");
            thread.setDaemon(true);
            return thread;
        });
    }
}
