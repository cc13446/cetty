package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.event.executor.scheduler.ScheduledFuture;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理多个 Event Executor
 *
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * 下一个事件执行器
     *
     * @return executor
     */
    EventExecutor next();

    /**
     * 优雅关闭
     *
     * @return future
     */
    Future<?> shutdownGracefully();

    /**
     * 优雅关闭
     *
     * @param quietPeriod quietPeriod
     * @param timeout     timeout
     * @param unit        time unit
     * @return future
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * @return 是否被关闭
     */
    boolean isShuttingDown();

    /**
     * @return future
     */
    Future<?> terminationFuture();

    @Override
    void shutdown();

    @Override
    List<Runnable> shutdownNow();

    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
