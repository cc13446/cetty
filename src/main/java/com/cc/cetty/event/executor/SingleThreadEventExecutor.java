package com.cc.cetty.event.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private final Queue<Runnable> taskQueue;

    private final RejectedExecutionHandler rejectedExecutionHandler;

    private volatile boolean start = false;

    private Thread thread;

    public SingleThreadEventExecutor() {
        this.taskQueue = newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
        this.rejectedExecutionHandler = new ThreadPoolExecutor.AbortPolicy();
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
        if (start) {
            return;
        }
        start = true;
        // 执行run方法，对io事件进行处理
        thread = new Thread(SingleThreadEventExecutor.this::run);
        thread.start();
        log.info("事件处理线程启动");
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
        // rejectedExecutionHandler.rejectedExecution(task, this);
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
    public void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (Objects.nonNull(thread)) {
            thread.interrupt();
        }
    }
}
