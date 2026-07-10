package com.lunacode.tui;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledAnimationTickerTest {
    @Test
    void startSchedulesOneReusableTask() {
        ManualScheduler scheduler = new ManualScheduler();
        ScheduledAnimationTicker ticker = new ScheduledAnimationTicker(scheduler, Duration.ofMillis(10));
        AtomicInteger first = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();

        ticker.start(first::incrementAndGet);
        ticker.start(duplicate::incrementAndGet);
        scheduler.runOnce();
        scheduler.runOnce();

        assertTrue(ticker.running());
        assertEquals(1, scheduler.scheduleCount());
        assertEquals(2, first.get());
        assertEquals(0, duplicate.get());
    }

    @Test
    void stopCancelsCallbacksAndAllowsRestart() {
        ManualScheduler scheduler = new ManualScheduler();
        ScheduledAnimationTicker ticker = new ScheduledAnimationTicker(scheduler, Duration.ofMillis(10));
        AtomicInteger ticks = new AtomicInteger();
        ticker.start(ticks::incrementAndGet);
        scheduler.runOnce();

        ticker.stop();
        scheduler.runOnce();
        assertFalse(ticker.running());
        assertEquals(1, ticks.get());

        ticker.start(ticks::incrementAndGet);
        scheduler.runOnce();
        assertTrue(ticker.running());
        assertEquals(2, scheduler.scheduleCount());
        assertEquals(2, ticks.get());
    }

    @Test
    void closeIsIdempotentAndPreventsRestart() {
        ManualScheduler scheduler = new ManualScheduler();
        ScheduledAnimationTicker ticker = new ScheduledAnimationTicker(scheduler, Duration.ofMillis(10));
        AtomicInteger ticks = new AtomicInteger();
        ticker.start(ticks::incrementAndGet);

        ticker.close();
        ticker.close();
        scheduler.runOnce();

        assertFalse(ticker.running());
        assertEquals(0, ticks.get());
        assertThrows(IllegalStateException.class, () -> ticker.start(ticks::incrementAndGet));
    }

    @Test
    void callbackFailureDoesNotCancelFutureFrames() {
        ManualScheduler scheduler = new ManualScheduler();
        ScheduledAnimationTicker ticker = new ScheduledAnimationTicker(scheduler, Duration.ofMillis(10));
        AtomicInteger attempts = new AtomicInteger();
        ticker.start(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("render failed");
        });

        scheduler.runOnce();
        scheduler.runOnce();

        assertTrue(ticker.running());
        assertEquals(2, attempts.get());
    }

    @Test
    void callbackDoesNotHoldTickerLockWhileRendering() {
        ManualScheduler scheduler = new ManualScheduler();
        ScheduledAnimationTicker ticker = new ScheduledAnimationTicker(scheduler, Duration.ofMillis(10));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ticker.start(() -> {
                Future<?> stopped = executor.submit(ticker::stop);
                try {
                    stopped.get(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("ticker callback 持锁导致 stop 无法完成", e);
                }
            });

            scheduler.runOnce();

            assertFalse(ticker.running());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidPeriod() {
        ManualScheduler scheduler = new ManualScheduler();

        assertThrows(IllegalArgumentException.class,
                () -> new ScheduledAnimationTicker(scheduler, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ScheduledAnimationTicker(scheduler, Duration.ofNanos(1)));
    }

    private static final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private boolean shutdown;
        private ManualFuture future;
        private int scheduleCount;

        void runOnce() {
            if (future != null && !future.isCancelled()) {
                future.command.run();
            }
        }

        int scheduleCount() {
            return scheduleCount;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command,
                long initialDelay,
                long period,
                TimeUnit unit
        ) {
            scheduleCount++;
            future = new ManualFuture(command);
            return future;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ManualFuture implements ScheduledFuture<Object> {
        private final Runnable command;
        private boolean cancelled;

        private ManualFuture(Runnable command) {
            this.command = command;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
    }
}
