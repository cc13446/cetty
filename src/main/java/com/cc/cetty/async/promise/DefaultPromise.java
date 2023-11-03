package com.cc.cetty.async.promise;

import com.cc.cetty.async.future.AbstractFuture;
import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.listener.FutureListenerHolder;
import com.cc.cetty.async.listener.GenericFutureListener;
import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.utils.AssertUtils;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 默认的 promise 实现类
 *
 * @author: cc
 * @date: 2023/11/1
 */
public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {

    /**
     * 通知监听器开始执行
     *
     * @param eventExecutor 负责执行监听器的线程
     * @param future        future
     * @param listener      listener
     */
    protected static void notifyListener(EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> listener) {
        AssertUtils.checkNotNull(eventExecutor);
        AssertUtils.checkNotNull(future);
        AssertUtils.checkNotNull(listener);

        if (eventExecutor.inEventLoop(Thread.currentThread())) {
            // 如果执行任务的线程是此线程，那么直接通知监听器执行方法
            notifyListener0(future, listener);
        }
        // 如果不是此线程，则包装成runnable，交给执行器去执行
        safeExecute(eventExecutor, () -> notifyListener0(future, listener));
    }

    /**
     * 通知监听器执行它的方法
     *
     * @param future   future
     * @param listener listener
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void notifyListener0(Future future, GenericFutureListener listener) {
        try {
            listener.operationComplete(future);
        } catch (Throwable t) {
            throw new RuntimeException("Fail to notify listener", t);
        }
    }

    /**
     * 安全执行
     *
     * @param executor 执行器
     * @param task     任务
     */
    private static void safeExecute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }

    /**
     * @param result result
     * @return 是否被取消
     */
    private static boolean isCancelled0(Object result) {
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    /**
     * @param result result
     * @return 是否完成
     */
    private static boolean isDone0(Object result) {
        return Objects.nonNull(result) && result != UNCANCELLABLE;
    }

    /**
     * 包装内部异常
     */
    private static final class CauseHolder {

        final Throwable cause;

        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    /**
     * 无结果任务执行成功时 result 的值
     */
    private static final Object SUCCESS = new Object();

    /**
     * 不可取消任务 result 的值
     */
    private static final Object UNCANCELLABLE = new Object();

    /**
     * 原子更新器，更新result的值
     */
    @SuppressWarnings({"rawtypes"})
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER = AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");

    /**
     * promise的执行器
     */
    private final EventExecutor executor;

    /**
     * 执行结果
     */
    private volatile Object result;

    /**
     * 所有监听器
     */
    private FutureListenerHolder listeners;

    /**
     * 记录阻塞线程数
     */
    private short waiters;

    /**
     * 防止并发通知的情况出现，如果为ture，则说明有线程通知监听器了，为false则说明没有。
     */
    private boolean notifyingListeners;

    public DefaultPromise() {
        this.executor = null;
    }

    public DefaultPromise(EventExecutor executor) {
        AssertUtils.checkNotNull(executor, "executor cannot be null");
        this.executor = executor;
    }

    /**
     * @return 执行器 可能为 null
     */
    protected EventExecutor executor() {
        return executor;
    }

    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("Promise complete already: " + this);
    }

    @Override
    public boolean trySuccess(V result) {
        return setSuccess0(result);
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            return this;
        }
        throw new IllegalStateException("Promise complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 当前 result 为空 可以被取消
        if (Objects.isNull(RESULT_UPDATER.get(this)) && RESULT_UPDATER.compareAndSet(this, null, new CauseHolder(new CancellationException()))) {
            notifyWaiters();
            notifyListeners();
            return true;
        }
        return false;
    }

    @Override
    public boolean setUncancellable() {
        if (RESULT_UPDATER.compareAndSet(this, null, UNCANCELLABLE)) {
            return true;
        }
        // 对应两种情况
        // 一是任务已经执行成功了，无法取消
        // 二就是任务已经被别的线程取消了
        Object result = this.result;

        // 如果没成功，也没取消，说明已经被设置为不可取消
        return !isDone0(result) || !isCancelled0(result);
    }

    @Override
    public boolean isCancellable() {
        return Objects.nonNull(result);
    }

    @Override
    public boolean isSuccess() {
        Object result = this.result;
        // result不为空，并且不等于被取消，并且不属于被包装过的异常类
        return Objects.nonNull(result) && result != UNCANCELLABLE && !(result instanceof CauseHolder);
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        AssertUtils.checkNotNull(listener);
        synchronized (this) {
            addListener0(listener);
        }
        if (isDone()) {
            notifyListeners();
        }
        return this;
    }

    @Override
    public Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        AssertUtils.checkNotNull(listener);
        synchronized (this) {
            removeListener0(listener);
        }

        return this;
    }

    @Override
    public Promise<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> await() throws InterruptedException {
        await0(0, false);
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getNow() {
        Object result = this.result;
        if (result instanceof CauseHolder || result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        return (V) result;
    }

    @Override
    public Throwable cause() {
        Object result = this.result;
        // 如果得到的结果属于包装过的异常类，说明任务执行时是有异常的，直接从包装过的类中得到异常属性即可
        // 如果不属于包装过的异常类，则直接返回null即可
        return (result instanceof CauseHolder) ? ((CauseHolder) result).cause : null;
    }

    /**
     * 检查是否为死锁
     */
    protected void checkDeadLock() {
        EventExecutor e = executor();
        // 判断是否为死锁, 即当前线程等待当前线程完成任务
        if (Objects.nonNull(e) && e.inEventLoop(Thread.currentThread())) {
            throw new RuntimeException("DeadLock: " + this);
        }
    }

    /**
     * 通知所有的监听器开始执行方法
     */
    private void notifyListeners() {
        if (Objects.isNull(listeners)) {
            return;
        }
        // 得到执行器
        EventExecutor executor = executor();
        // 如果正在执行方法的线程就是执行器的线程，就立刻通知监听器执行方法
        if (executor.inEventLoop(Thread.currentThread())) {
            notifyListenersNow();
            return;
        }
        safeExecute(executor, this::notifyListenersNow);
    }

    /**
     * 通知所有的监听器开始执行方法
     */
    private void notifyListenersNow() {
        FutureListenerHolder listeners;
        synchronized (this) {
            // 如果notifyingListeners如果为ture，说明已经有线程通知监听器了
            // 或者当监听器属性为null，直接返回即可
            if (notifyingListeners || Objects.isNull(this.listeners)) {
                return;
            }
            notifyingListeners = true;
            listeners = this.listeners;
            this.listeners = null;
        }
        while (!Thread.interrupted()) {
            notifyListeners0(listeners);
            // 通知完成后继续上锁, 方法结束之后notifyingListeners的值要重置
            synchronized (this) {
                if (Objects.isNull(this.listeners)) {
                    notifyingListeners = false;
                    return;
                }
                // 如果走到这里就说明在将要重置notifyingListeners之前，又添加了监听器
                // 这时候要重复上一个synchronized代码块中的内容，为下一次循环作准备
                // 在循环的时候也有可能有其他线程来通知监听器执行方法，但 this.listeners == null
                // 而且notifyingListeners为ture，所以线程会被挡在第一个synchronized块之前
                listeners = this.listeners;
                this.listeners = null;
            }
        }
    }

    /**
     * 通知所有的监听器开始执行
     */
    private void notifyListeners0(FutureListenerHolder listeners) {
        GenericFutureListener<?>[] a = listeners.listeners();
        int size = listeners.size();
        for (int i = 0; i < size; i++) {
            notifyListener0(this, a[i]);
        }
    }

    /**
     * 添加监听器
     *
     * @param listener listener
     */
    private void addListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (Objects.isNull(listeners)) {
            listeners = new FutureListenerHolder(listener);
        } else {
            listeners.add(listener);
        }
    }

    /**
     * 删除监听器
     *
     * @param listener listener
     */
    private void removeListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (Objects.nonNull(listener)) {
            listeners.remove(listener);
        }
    }

    /**
     * 设置成功结果
     *
     * @param result result
     * @return 是否成功
     */
    private boolean setSuccess0(V result) {
        // 设置成功结果，如果结果为null，则将SUCCESS赋值给result
        return setValue0(Objects.isNull(result) ? SUCCESS : result);
    }

    /**
     * 设置失败结果
     *
     * @param cause cause
     * @return 是否成功
     */
    private boolean setFailure0(Throwable cause) {
        // 设置失败结果，也就是包装过的异常信息
        return setValue0(new CauseHolder(AssertUtils.checkNotNull(cause)));
    }

    /**
     * 设置值
     *
     * @param objResult result
     * @return 是否成功
     */
    private boolean setValue0(Object objResult) {
        // result还未被赋值时，原子更新器可以将结果赋值给result
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) || RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {
            notifyWaiters();
            notifyListeners();
            return true;
        }
        return false;
    }

    /**
     * 唤醒所有等待者
     */
    private synchronized void notifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
    }

    /**
     * 增加等待者数量
     */
    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("Too many waiters: " + this);
        }
        ++waiters;
    }

    /**
     * 减少等待者数量
     */
    private void decWaiters() {
        --waiters;
    }

    /**
     * 任务执行失败则抛出异常
     */
    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (Objects.isNull(cause)) {
            return;
        }
        throw new RuntimeException(cause);
    }

    /**
     * 真正让线程限时阻塞等待的方法
     *
     * @param timeoutNanos 超时时间 ns
     * @param timeout      超时时间是否生效
     * @return 是否完成
     * @throws InterruptedException 中断异常
     */
    private boolean await0(long timeoutNanos, boolean timeout) throws InterruptedException {
        if (isDone()) {
            return true;
        }

        if (timeoutNanos <= 0 && timeout) {
            return isDone();
        }

        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        // 检查是否死锁，如果是死锁直接抛出异常
        // 在这里可以进一步思考一下，哪些情况会发生死锁
        // 如果熟悉了netty之后，就会发现，凡是结果要赋值到promise的任务都是由netty中的单线程执行器来执行的
        // 执行每个任务的执行器是和channel绑定的。如果某个执行器正在执行任务，但是还未获得结果
        // 这时候该执行器又来获取结果，一个线程怎么能同时执行任务又要唤醒自己呢，所以必然会产生死锁
        checkDeadLock();

        long startTime = System.nanoTime();
        long waitTime = timeoutNanos;

        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    if (timeout) {
                        wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } else {
                        wait();
                    }
                } finally {
                    decWaiters();
                }
            }
            if (isDone()) {
                return true;
            } else if (timeout) {
                // 可能是虚假唤醒
                // 得到新的等待时间
                // 如果等待时间小于0，表示已经阻塞了用户设定的等待时间
                // 如果waitTime大于0，则继续阻塞
                waitTime = timeoutNanos - (System.nanoTime() - startTime);
                if (waitTime <= 0) {
                    return isDone();
                }
            }
        }

        return isDone();
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    /**
     * @return 当前实例的 toString
     */
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64).append(this.getClass().getSimpleName()).append('@').append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure: ").append(((CauseHolder) result).cause).append(')');
        } else if (result != null) {
            buf.append("(success: ").append(result).append(')');
        } else {
            buf.append("(incomplete)");
        }

        return buf;
    }
}
