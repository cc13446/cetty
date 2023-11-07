package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.listener.FutureListener;
import com.cc.cetty.async.promise.DefaultPromise;
import com.cc.cetty.async.promise.Promise;
import com.cc.cetty.event.executor.chooser.DefaultEventExecutorChooserFactory;
import com.cc.cetty.event.executor.chooser.EventExecutorChooserFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多线程事件执行器组
 *
 * @author: cc
 * @date: 2023/10/31
 */
public abstract class MultiThreadEventExecutorGroup implements EventExecutorGroup {

    /**
     * 事件执行器数组
     */
    private final EventExecutor[] children;

    /**
     * 只读的 executor
     */
    private final Set<EventExecutor> readonlyChildren;

    /**
     * 停止的 executor 数量
     */
    private final AtomicInteger terminatedChildren = new AtomicInteger();

    /**
     * 关闭 future
     */
    private final Promise<?> terminationFuture = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);

    /**
     * executor 选择器
     */
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    protected MultiThreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, Objects.isNull(threadFactory) ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    protected MultiThreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    protected MultiThreadEventExecutorGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        if (Objects.nonNull(executor)) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }
        // 这里创建了执行器数组
        children = new EventExecutor[nThreads];
        for (int i = 0; i < nThreads; i++) {
            boolean success = false;
            try {
                // 创建单线程执行器
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    // 没创建成功，就关闭单线程执行器
                    for (int j = 0; j < i; j++) {
                        children[j].shutdownGracefully();
                    }
                    for (int j = 0; j < i; j++) {
                        EventExecutor e = children[j];
                        try {
                            // 判断状态，如果单线程执行器还没关闭，就等待一会
                            // 这里阻塞的其实是主线程，这个一定要搞清楚，要弄清楚执行当前构造方法的是哪个线程
                            while (!e.isTerminated()) {
                                // 这个方法内部会阻塞住
                                // 注意，这里是个很小的while循环，所以会阻塞结束后继续判断
                                // 当然，单线程执行器关闭后阻塞就会结束了，不会真的阻塞Integer.MAX_VALUE这么久
                                // 内部其实是用到了CountDownLatch
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        // 创建选择器
        chooser = chooserFactory.newChooser(children);

        // 这里就是创建了一个监听器，然后在监听器内部判断当前执行器数组中的所有执行器是否关闭
        // 如果都关闭了，就设置terminationFuture成功
        final FutureListener<Object> terminationListener = future -> {
            // 原子计数也用上了，直到等于数组长度，就是全部关闭了
            if (terminatedChildren.incrementAndGet() == children.length) {
                terminationFuture.setSuccess(null);
            }
        };

        for (EventExecutor e : children) {
            // 在这里，把刚才创建的监听器添加到每个单线程执行器的promise上，这样关闭成功，就会回调这个方法
            e.terminationFuture().addListener(terminationListener);
        }
        Set<EventExecutor> childrenSet = new LinkedHashSet<>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }

    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l : children) {
            // 在这里调用每一个执行器的shutdownGracefully方法，逻辑其实很简单的，就顺着方法向下点就行
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        // 返回的future就是terminationFuture成员变量
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    /**
     * @return 执行器数量
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * 抽象工厂方法的实现，具体用什么 Executor 现在不知道
     *
     * @param executor executor
     * @param args args
     * @return 具体的 Executor
     * @throws Exception exception
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    @Override
    public void shutdown() {
        for (EventExecutor l : children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l : children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l : children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l : children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop:
        for (EventExecutor l : children) {
            for (; ; ) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }

}
