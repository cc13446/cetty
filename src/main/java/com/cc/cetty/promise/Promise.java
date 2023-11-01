package com.cc.cetty.promise;


import com.cc.cetty.promise.future.Future;
import com.cc.cetty.promise.listener.GenericFutureListener;

/**
 * promise 接口
 *
 * @author: cc
 * @date: 2023/11/1
 */
public interface Promise<V> extends Future<V> {

    /**
     * @param result result
     * @return this
     */
    Promise<V> setSuccess(V result);

    /**
     * @param result result
     * @return 是否设置成功
     */
    boolean trySuccess(V result);

    /**
     * @param cause cause
     * @return this
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * @param cause cause
     * @return 是否设置成功
     */
    boolean tryFailure(Throwable cause);

    /**
     * 设置不可取消
     *
     * @return 是否设置成功
     */
    boolean setUncancellable();

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptible();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptible();
}
