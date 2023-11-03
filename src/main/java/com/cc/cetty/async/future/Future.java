package com.cc.cetty.async.future;

import com.cc.cetty.async.listener.GenericFutureListener;

import java.util.concurrent.TimeUnit;

/**
 * 对原本的 Future 进行扩展
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
     * @return 获取错误
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
     * 删除回调
     *
     * @param listener listener
     * @return this
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * 同步阻塞
     * 有异常直接抛出
     *
     * @return this
     * @throws InterruptedException 中断异常
     */
    Future<V> sync() throws InterruptedException;

    /**
     * 同步阻塞
     *
     * @return this
     * @throws InterruptedException 中断异常
     */
    Future<V> await() throws InterruptedException;

    /**
     * 限时同步阻塞
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 是否完成
     * @throws InterruptedException 中断异常
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 限时同步阻塞
     *
     * @param timeoutMillis 超时时间 单位为ms
     * @return 是否完成
     * @throws InterruptedException 中断异常
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * 立即获取值
     *
     * @return 结果
     */
    V getNow();
}
