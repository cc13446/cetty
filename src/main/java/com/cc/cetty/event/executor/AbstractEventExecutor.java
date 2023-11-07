package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.promise.DefaultPromise;
import com.cc.cetty.async.promise.Promise;
import com.cc.cetty.async.promise.PromiseTask;
import com.cc.cetty.event.executor.scheduler.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author: cc
 * @date: 2023/11/07
 **/
@Slf4j
public abstract class AbstractEventExecutor extends AbstractExecutorService implements EventExecutor {

    /**
     * 关闭静默期，默认为2秒
     * 意思是当单线程执行器关闭时，最好等单线程执行器中的任务全都执行完后，才关闭最好。
     * 如果在这两秒内，用户没有再提交任务，就可以关闭了
     * 如果用户提交了任务，就不能关闭，要把任务执行完才行
     */
    static final long DEFAULT_SHUTDOWN_QUIET_PERIOD = 2;

    /**
     * 这个是超时时间，超过这个时间，尽管任务队列中还有任务，也要结束单线程执行器了
     */
    static final long DEFAULT_SHUTDOWN_TIMEOUT = 15;

    private final EventExecutorGroup parent;

    private final Collection<EventExecutor> selfCollection = Collections.<EventExecutor>singleton(this);

    protected AbstractEventExecutor() {
        this(null);
    }

    protected AbstractEventExecutor(EventExecutorGroup parent) {
        this.parent = parent;
    }

    @Override
    public EventExecutorGroup parent() {
        return parent;
    }

    @Override
    public EventExecutor next() {
        return this;
    }


    @Override
    public Iterator<EventExecutor> iterator() {
        return selfCollection.iterator();
    }

    @Override
    public Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }


    @Override
    @Deprecated
    public abstract void shutdown();

    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public <V> Promise<V> newPromise() {
        return new DefaultPromise<>(this);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return (Future<?>) super.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return (Future<T>) super.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return (Future<T>) super.submit(task);
    }

    @Override
    protected final <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new PromiseTask<>(this, runnable, value);
    }

    @Override
    protected final <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new PromiseTask<>(this, callable);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay,
                                       TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    protected static void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.warn("A task raised an exception. Task: {}", task, t);
        }
    }
}


