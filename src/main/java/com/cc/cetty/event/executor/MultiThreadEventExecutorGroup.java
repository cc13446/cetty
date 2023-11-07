package com.cc.cetty.event.executor;

import com.cc.cetty.event.executor.chooser.DefaultEventExecutorChooserFactory;
import com.cc.cetty.event.executor.chooser.EventExecutorChooserFactory;
import com.cc.cetty.event.executor.factory.DefaultThreadFactory;
import com.cc.cetty.event.executor.thread.ThreadPerTaskExecutor;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * 多线程事件执行器组
 *
 * @author: cc
 * @date: 2023/10/31
 */
public abstract class MultiThreadEventExecutorGroup extends AbstractEventExecutorGroup {

    /**
     * 事件执行器数组
     */
    private final EventExecutor[] children;

    /**
     * executor 选择器
     */
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    /**
     * @param nThreads      线程数量
     * @param threadFactory 线程工厂
     * @param args          交给线程的参数
     */
    protected MultiThreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, Objects.isNull(threadFactory) ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * @param nThreads 线程数量
     * @param executor 线程执行器
     * @param args     交给线程的参数
     */
    protected MultiThreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * @param nThreads       线程数量
     * @param executor       线程执行器
     * @param chooserFactory 线程选择器
     * @param args           交给线程的参数
     */
    protected MultiThreadEventExecutorGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }
        if (Objects.nonNull(executor)) {
            executor = new ThreadPerTaskExecutor(new DefaultThreadFactory(getClass()));
        }
        // 这里创建了执行器数组
        this.children = new EventExecutor[nThreads];
        for (int i = 0; i < nThreads; i++) {
            try {
                children[i] = newChild(executor, args);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create a child event loop", e);
            }
            // 这里应该有一段关闭逻辑的，如果创建没成功，需要创建的线程都关掉
        }
        // 创建选择器
        this.chooser = chooserFactory.newChooser(children);
    }

    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    /**
     * 抽象工厂方法的实现，具体用什么 Executor 现在不知道
     *
     * @param executor executor
     * @param args     args
     * @return 具体的 Executor
     * @throws Exception exception
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

}