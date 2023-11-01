package com.cc.cetty.promise.future;

import com.cc.cetty.promise.listener.GenericFutureListener;

import java.util.concurrent.TimeUnit;

/**
 * 对原本的Future进行扩展
 *
 * @author: cc
 * @date: 2023/11/1
 */
public interface Future<V> extends java.util.concurrent.Future<V> {

    /**
     * @return 是否成功
     */
    boolean isSuccess();

    /**
     * @return 是否可以被取消
     */
    boolean isCancellable();

    /**
     * @return 报错
     */
    Throwable cause();

    /**
     * 添加回调
     *
     * @param listener listener
     * @return this
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * 添加回调
     *
     * @param listeners listeners
     * @return this
     */
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 删除回调
     *
     * @param listener listener
     * @return this
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * 删除回调
     *
     * @param listeners listeners
     * @return this
     */
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 同步阻塞
     * 有异常直接抛出
     *
     * @return this
     * @throws InterruptedException 中断异常
     */
    Future<V> sync() throws InterruptedException;

    /**
     * 同步阻塞 不可被打断
     * 有异常直接抛出
     *
     * @return this
     */
    Future<V> syncUninterruptible();

    /**
     * 同步阻塞
     *
     * @return this
     * @throws InterruptedException 中断异常
     */
    Future<V> await() throws InterruptedException;

    /**
     * 同步阻塞 不可被打断
     *
     * @return this
     */
    Future<V> awaitUninterruptible();

    /**
     * 限时同步阻塞
     *
     * @param timeout timeout
     * @param unit    unit
     * @return 是否完成
     * @throws InterruptedException 中断异常
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 限时同步阻塞
     *
     * @param timeoutMillis time ns
     * @return 是否完成
     * @throws InterruptedException 中断异常
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * 限时同步阻塞
     * 不可打断
     *
     * @param timeout timeout
     * @param unit    unit
     * @return 是否完成
     */
    boolean awaitUninterruptible(long timeout, TimeUnit unit);

    /**
     * 限时同步阻塞
     * 不可打断
     *
     * @param timeoutMillis time ns
     * @return 是否完成
     */
    boolean awaitUninterruptible(long timeoutMillis);


    /**
     * 立即获取值
     *
     * @return 结果
     */
    V getNow();

}
