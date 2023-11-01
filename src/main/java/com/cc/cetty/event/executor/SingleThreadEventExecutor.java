package com.cc.cetty.event.executor;

import com.cc.cetty.event.reject.RejectedExecutionHandlers;
import com.cc.cetty.event.factory.EventLoopTaskQueueFactory;
import com.cc.cetty.event.reject.RejectedExecutionHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 单线程的事件执行器
 *
 * @author: cc
 * @date: 2023/10/31
 */
@Slf4j
public abstract class SingleThreadEventExecutor implements EventExecutor {

    /**
     * 任务队列的容量，默认是Integer的最大值
     */
    protected static final int DEFAULT_MAX_PENDING_TASKS = Integer.MAX_VALUE;

    private static final int ST_NOT_STARTED = 0;

    private static final int ST_STARTED = 1;

    private volatile int state = ST_NOT_STARTED;

    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");

    private final Queue<Runnable> taskQueue;

    private final RejectedExecutionHandler rejectedExecutionHandler;

    private Executor executor;

    private Thread thread;

    protected SingleThreadEventExecutor(Executor executor, EventLoopTaskQueueFactory queueFactory, ThreadFactory threadFactory) {
        this(executor, queueFactory, threadFactory, RejectedExecutionHandlers.reject());
    }

    protected SingleThreadEventExecutor(Executor executor, EventLoopTaskQueueFactory queueFactory, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
        if (Objects.isNull(executor)) {
            this.executor = new ThreadPerTaskExecutor(threadFactory);
        }
        this.taskQueue = queueFactory == null ? newTaskQueue(DEFAULT_MAX_PENDING_TASKS) : queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
        this.rejectedExecutionHandler = rejectedExecutionHandler;
    }

    /**
     * 新建任务队列
     *
     * @param maxPendingTasks 最长队列长度
     * @return 队列
     */
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<>(maxPendingTasks);
    }

    @Override
    public void execute(Runnable task) {
        addTask(task);
        startThread();
    }

    /**
     * 开启事件处理线程
     */
    private void startThread() {
        if (state == ST_NOT_STARTED) {
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    doStartThread();
                    success = true;
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    /**
     * 开启事件处理线程
     */
    private void doStartThread() {
        executor.execute(() -> {
            thread = Thread.currentThread();
            SingleThreadEventExecutor.this.run();
            log.error("单线程执行器的线程执行错误");
        });
    }


    /**
     * 对io事件进行处理
     */
    protected abstract void run();

    /**
     * 添加任务
     *
     * @param task 任务
     */
    private void addTask(Runnable task) {
        if (Objects.isNull(task)) {
            throw new NullPointerException("Task cannot be null");
        }
        //如果添加失败，执行拒绝策略
        if (!offerTask(task)) {
            rejectTask(task);
        }
    }

    /**
     * 添加任务
     *
     * @param task 任务
     * @return 是否成功
     */
    private boolean offerTask(Runnable task) {
        return taskQueue.offer(task);
    }

    /**
     * 拒绝任务
     *
     * @param task 任务
     */
    private void rejectTask(Runnable task) {
        rejectedExecutionHandler.rejected(task, this);
    }

    /**
     * @return 是否还有任务
     */
    protected boolean hasTask() {
        return !taskQueue.isEmpty();
    }

    /**
     * 运行所有的任务
     */
    protected void runAllTasks() {
        runAllTasksFrom(taskQueue);
    }

    /**
     * @param taskQueue queue
     * @return 从队列中拉取的任务
     */
    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        return taskQueue.poll();
    }

    /**
     * @param taskQueue taskQueue
     */
    protected void runAllTasksFrom(Queue<Runnable> taskQueue) {
        Runnable task = pollTaskFrom(taskQueue);
        if (Objects.isNull(task)) {
            return;
        }
        do {
            safeExecute(task);
        } while (Objects.nonNull(task = pollTaskFrom(taskQueue)));
    }

    /**
     * @param task 任务
     */
    private void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    @Override
    public void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (Objects.nonNull(thread)) {
            thread.interrupt();
        }
    }
}
