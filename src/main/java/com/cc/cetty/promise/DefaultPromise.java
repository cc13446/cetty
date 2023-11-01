package com.cc.cetty.promise;

import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.promise.future.AbstractFuture;
import com.cc.cetty.promise.future.Future;
import com.cc.cetty.promise.listener.DefaultFutureListeners;
import com.cc.cetty.promise.listener.GenericFutureListener;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {

    /**
     * 通知监听器开始执行方法
     *
     * @param eventExecutor executor
     * @param future        future
     * @param listener      listener
     */
    protected static void notifyListener(EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> listener) {
        assert Objects.nonNull(eventExecutor) : "eventExecutor cannot be null";
        assert Objects.nonNull(future) : "eventExecutor cannot be future";
        assert Objects.nonNull(listener) : "eventExecutor cannot be listener";

        if (eventExecutor.inEventLoop(Thread.currentThread())) {
            // 如果执行任务的线程是单线程执行器，那么直接通知监听器执行方法
            notifyListener0(future, listener);
        }
        // 如果不是执行器的线程，则包装成runnable，交给执行器去通知监听器执行方法
        safeExecute(eventExecutor, () -> notifyListener0(future, listener));
    }

    /**
     * 通知监听器执行它的方法
     *
     * @param future future
     * @param listen listen
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void notifyListener0(Future future, GenericFutureListener listen) {
        try {
            listen.operationComplete(future);
        } catch (Throwable t) {
            throw new RuntimeException(t);
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
     * 无结果任务执行成功时 result的值
     */
    private static final Object SUCCESS = new Object();

    /**
     * 不可取消任务 result的值
     */
    private static final Object UNCANCELLABLE = new Object();

    /**
     * 原子更新器，更新result的值
     */
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER
            = AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");

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
    private DefaultFutureListeners listeners;

    /**
     * 记录阻塞线程数
     */
    private short waiters;

    /**
     * 防止并发通知的情况出现，如果为ture，则说明有线程通知监听器了，为false则说明没有。
     */
    private boolean notifyingListeners;

    public DefaultPromise(EventExecutor executor) {
        assert Objects.nonNull(executor) : "executor cannot be null";
        this.executor = executor;
    }

    /**
     * @return 执行器
     */
    protected EventExecutor executor() {
        return executor;
    }

    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("promise complete already: " + this);
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
        throw new IllegalStateException("promise complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
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
    public boolean isSuccess() {
        Object result = this.result;
        // result不为空，并且不等于被取消，并且不属于被包装过的异常类
        return Objects.nonNull(result) && result != UNCANCELLABLE && !(result instanceof CauseHolder);
    }

    @Override
    public boolean isCancellable() {
        return Objects.nonNull(result);
    }

    @Override
    public Throwable cause() {
        Object result = this.result;
        // 如果得到的结果属于包装过的异常类，说明任务执行时是有异常的，直接从包装过的类中得到异常属性即可
        // 如果不属于包装过的异常类，则直接返回null即可
        return (result instanceof CauseHolder) ? ((CauseHolder) result).cause : null;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        assert Objects.nonNull(listener) : "Listener cannot be null";

        synchronized (this) {
            addListener0(listener);
        }
        if (isDone()) {
            notifyListeners();
        }
        return this;
    }

    @SafeVarargs
    @Override
    public final Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        assert Objects.nonNull(listeners) : "Listeners cannot be null";
        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                if (listener == null) {
                    break;
                }
                addListener0(listener);
            }
        }
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener) {
        assert Objects.nonNull(listener) : "Listener cannot be null";
        synchronized (this) {
            removeListener0(listener);
        }

        return this;
    }

    @SafeVarargs
    @Override
    public final Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        assert Objects.nonNull(listeners) : "Listeners cannot be null";
        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                if (listener == null) {
                    break;
                }
                removeListener0(listener);
            }
        }
        return this;
    }

    @Override
    public Promise<V> await() throws InterruptedException {
        if (isDone()) {
            return this;
        }
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        // 检查是否死锁，如果是死锁直接抛出异常
        // 在这里可以进一步思考一下，哪些情况会发生死锁
        // 如果熟悉了netty之后，就会发现，凡是结果要赋值到promise的任务都是由netty中的单线程执行器来执行的
        // 执行每个任务的执行器是和channel绑定的。如果某个执行器正在执行任务，但是还未获得结果，这时候该执行器
        // 又来获取结果，一个线程怎么能同时执行任务又要唤醒自己呢，所以必然会产生死锁
        checkDeadLock();
        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    wait();
                } finally {
                    decWaiters();
                }
            }
        }
        return this;
    }

    @Override
    public Promise<V> awaitUninterruptible() {
        if (isDone()) {
            return this;
        }
        checkDeadLock();
        boolean interrupted = false;
        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    decWaiters();
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

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
    public boolean awaitUninterruptible(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptible(long timeoutMillis) {
        try {
            return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
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
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 当前 result 为空 可以被取消
        if (Objects.isNull(RESULT_UPDATER.get(this)) && RESULT_UPDATER.compareAndSet(this, null, new CauseHolder(new CancellationException()))) {
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
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
    public Promise<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> syncUninterruptible() {
        awaitUninterruptible();
        rethrowIfFailed();
        return this;
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    /**
     * @return 当前实例的 toString
     */
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64)
                .append(this.getClass().getSimpleName())
                .append('@')
                .append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure: ")
                    .append(((CauseHolder) result).cause)
                    .append(')');
        } else if (result != null) {
            buf.append("(success: ")
                    .append(result)
                    .append(')');
        } else {
            buf.append("(incomplete)");
        }

        return buf;
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
        DefaultFutureListeners listeners;
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
     * 通知所有的监听器开始执行方法
     */
    private void notifyListeners0(DefaultFutureListeners listeners) {
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
        // listeners为null，则说明在这之前没有添加监听器，直接把该监听器赋值给属性即可
        // 外层加了同步
        if (Objects.isNull(listeners)) {
            listeners = new DefaultFutureListeners(listener);
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
        assert Objects.nonNull(cause) : "cause cannot be null";
        return setValue0(new CauseHolder(cause));
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
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
    }

    /**
     * 唤醒所有等待者
     *
     * @return 是否需要执行监听器
     */
    private synchronized boolean checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
        return listeners != null;
    }

    /**
     * 增加等待者数量
     */
    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
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
        if (cause == null) {
            return;
        }
        throw new RuntimeException(cause);
    }

    /**
     * 真正让线程阻塞等待的方法
     *
     * @param timeoutNanos  ns
     * @param interruptable 是否可以被打断
     * @return 是否完成
     * @throws InterruptedException 中断异常
     */
    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (isDone()) {
            return true;
        }
        if (timeoutNanos <= 0) {
            return isDone();
        }
        // interruptable为true则允许抛出中断异常
        // 检查当前线程是否被中断
        // 如果都为true则抛出中断异常
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        // 检查死锁
        checkDeadLock();
        // 获取当前纳秒时间
        long startTime = System.nanoTime();
        // 用户设置的等待时间
        long waitTime = timeoutNanos;
        // 是否中断
        boolean interrupted = false;
        try {
            for (; ; ) {
                synchronized (this) {
                    // 再次判断是否执行完成，防止出现竞争锁的时候，任务先完成了，而外部线程还没开始阻塞的情况
                    if (isDone()) {
                        return true;
                    }
                    incWaiters();
                    try {
                        wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            throw e;
                        } else {
                            interrupted = true;
                        }
                    } finally {
                        decWaiters();
                    }
                }
                // 走到这里说明线程被唤醒了
                if (isDone()) {
                    return true;
                } else {
                    // 可能是虚假唤醒。
                    // 得到新的等待时间
                    // 如果等待时间小于0，表示已经阻塞了用户设定的等待时间
                    // 如果waitTime大于0，则继续循环
                    waitTime = timeoutNanos - (System.nanoTime() - startTime);
                    if (waitTime <= 0) {
                        return isDone();
                    }
                }
            }
        } finally {
            // 退出方法前判断是否要给执行任务的线程添加中断标记
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
