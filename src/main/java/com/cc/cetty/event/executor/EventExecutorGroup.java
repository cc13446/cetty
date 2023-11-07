package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.event.executor.scheduler.ScheduledFuture;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 管理多个 Event Executor
 */
public interface EventExecutorGroup {

    /**
     * 下一个事件执行器
     *
     * @return executor
     */
    EventExecutor next();

    /**
     * 执行任务
     *
     * @param command command
     */
    void execute(Runnable command);

    /**
     * 提交任务
     *
     * @param task task
     * @return future
     */
    Future<?> submit(Runnable task);

    /**
     * 提交任务
     *
     * @param task   task
     * @param result result
     * @param <T>    T
     * @return future
     */
    <T> Future<T> submit(Runnable task, T result);

    /**
     * 提交任务
     *
     * @param task task
     * @param <T>  T
     * @return future
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * 提交定时任务
     *
     * @param command command
     * @param delay   delay
     * @param unit    时间单位
     * @return future
     */
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    /**
     * 提交定时任务
     *
     * @param callable callable
     * @param delay    delay
     * @param unit     时间单位
     * @param <V>      V
     * @return future
     */
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * 提交循环任务
     * 等待上一个执行完后才继续执行下一个
     *
     * @param command      command
     * @param initialDelay init delay
     * @param period       period
     * @param unit         时间单位
     * @return future
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * 提交循环任务
     * 方法会到了固定时间，不管上一个方法有没有执行完，都会立即执行下一个方法
     *
     * @param command      command
     * @param initialDelay init delay
     * @param delay        delay
     * @param unit         时间单位
     * @return future
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
