package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.event.executor.scheduler.AbstractScheduledEventExecutor;
import com.cc.cetty.event.executor.scheduler.ScheduledFutureTask;
import lombok.extern.slf4j.Slf4j;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
@Slf4j
public final class GlobalEventExecutor extends AbstractScheduledEventExecutor {


    private static final long SCHEDULE_QUIET_PERIOD_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    public static final GlobalEventExecutor INSTANCE = new GlobalEventExecutor();

    final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    final ScheduledFutureTask<Void> quietPeriodTask = new ScheduledFutureTask<>(
            this, Executors.callable(() -> {
                // NOOP
            }, null), ScheduledFutureTask.deadlineNanos(SCHEDULE_QUIET_PERIOD_INTERVAL), -SCHEDULE_QUIET_PERIOD_INTERVAL);


    final ThreadFactory threadFactory;
    private final TaskRunner taskRunner = new TaskRunner();
    private final AtomicBoolean started = new AtomicBoolean();
    volatile Thread thread;

    private GlobalEventExecutor() {
        scheduledTaskQueue().add(quietPeriodTask);
        threadFactory = new DefaultThreadFactory(DefaultThreadFactory.toPoolName(getClass()), false, Thread.NORM_PRIORITY, null);
    }


    Runnable takeTask() {
        BlockingQueue<Runnable> taskQueue = this.taskQueue;
        for (;;) {
            ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) {
                Runnable task = null;
                try {
                    task = taskQueue.take();
                } catch (InterruptedException e) {
                    // Ignore
                }
                return task;
            } else {
                long delayNanos = scheduledTask.delayNanos();
                Runnable task;
                if (delayNanos > 0) {
                    try {
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                } else {
                    task = taskQueue.poll();
                }

                if (task == null) {
                    fetchFromScheduledTaskQueue();
                    task = taskQueue.poll();
                }

                if (task != null) {
                    return task;
                }
            }
        }
    }

    private void fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        Runnable scheduledTask = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            taskQueue.add(scheduledTask);
            scheduledTask = pollScheduledTask(nanoTime);
        }
    }


    public int pendingTasks() {
        return taskQueue.size();
    }


    private void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        taskQueue.add(task);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }


    public boolean awaitInactivity(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        final Thread thread = this.thread;
        if (thread == null) {
            throw new IllegalStateException("thread was not started");
        }
        thread.join(unit.toMillis(timeout));
        return !thread.isAlive();
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        addTask(task);
        if (!inEventLoop(Thread.currentThread())) {
            startThread();
        }
    }

    private void startThread() {
        if (started.compareAndSet(false, true)) {
            final Thread t = threadFactory.newThread(taskRunner);
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                t.setContextClassLoader(null);
                return null;
            });
            thread = t;
            t.start();
        }
    }


    final class TaskRunner implements Runnable {
        @Override
        public void run() {
            for (;;) {
                Runnable task = takeTask();
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        log.warn("Unexpected exception from the global event executor: ", t);
                    }

                    if (task != quietPeriodTask) {
                        continue;
                    }
                }

                Queue<ScheduledFutureTask<?>> scheduledTaskQueue = GlobalEventExecutor.this.scheduledTaskQueue;
                if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                    boolean stopped = started.compareAndSet(true, false);
                    assert stopped;
                    if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                        break;
                    }
                    if (!started.compareAndSet(false, true)) {
                        break;
                    }

                }
            }
        }
    }
}
