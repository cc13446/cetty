package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.promise.Promise;

/**
 * 负责执行时间循环的线程执行者
 *
 * @author: cc
 * @date: 2023/10/31
 */
public interface EventExecutor extends EventExecutorGroup {

    /**
     * @param thread 线程
     * @return 这个线程是否在当前的事件执行器中
     */
    boolean inEventLoop(Thread thread);

    /**
     * @return 管理此线程的线程池
     */
    EventExecutorGroup parent();

    /**
     * 新建一个 Promise
     *
     * @param <V> V
     * @return promise
     */
    <V> Promise<V> newPromise();

    /**
     * @param result result
     * @param <V>    V
     * @return 已经成功的 future
     */
    <V> Future<V> newSucceededFuture(V result);

    /**
     *
     * @param cause cause
     * @return 已经失败的future
     * @param <V> V
     */
    <V> Future<V> newFailedFuture(Throwable cause);

}
