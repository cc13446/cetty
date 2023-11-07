package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.FailedFuture;
import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.future.SucceededFuture;
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
public abstract class AbstractEventExecutor implements EventExecutor {

    /**
     * 安全执行，不抛出异常
     * @param task task
     */
    protected static void safeExecute(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log.warn("A task raised an exception. Task: {}", task, t);
        }
    }

    /**
     * 线程池
     */
    protected final EventExecutorGroup parent;

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
    public <V> Promise<V> newPromise() {
        return new DefaultPromise<>(this);
    }

    @Override
    public <V> Future<V> newSucceededFuture(V result) {
        return new SucceededFuture<>(this, result);
    }

    @Override
    public <V> Future<V> newFailedFuture(Throwable cause) {
        return new FailedFuture<>(this, cause);
    }

}


