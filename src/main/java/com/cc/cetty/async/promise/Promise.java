package com.cc.cetty.async.promise;


import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.listener.GenericFutureListener;

/**
 * promise 接口
 * 对 future 接口做进一步扩展
 *
 * @author: cc
 * @date: 2023/11/1
 */
public interface Promise<V> extends Future<V> {

    /**
     * 设置成功
     *
     * @param result result
     * @return this
     */
    Promise<V> setSuccess(V result);

    /**
     * 尝试设置成功
     *
     * @param result result
     * @return 是否设置成功
     */
    boolean trySuccess(V result);

    /**
     * 设置失败
     *
     * @param cause cause
     * @return this
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * 尝试设置失败
     *
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
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> sync() throws InterruptedException;
}
