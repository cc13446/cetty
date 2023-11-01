package com.cc.cetty.event.executor;

import java.util.concurrent.TimeUnit;

/**
 * 多线程事件执行器组
 *
 * @author: cc
 * @date: 2023/10/31
 */
public abstract class MultiThreadEventExecutorGroup implements EventExecutorGroup {


    /**
     * 多个事件执行器
     */
    private final EventExecutor[] eventExecutor;

    /**
     * 当前事件执行器的下标
     */
    private int index = 0;

    /**
     * @param threads 线程数
     */
    public MultiThreadEventExecutorGroup(int threads) {
        eventExecutor = new EventExecutor[threads];
        for (int i = 0; i < threads; i++) {
            eventExecutor[i] = newChild();
        }
    }

    /**
     * 抽象工厂方法的实现，具体用什么 Executor 现在不知道
     *
     * @return 具体的 Executor
     */
    protected abstract EventExecutor newChild();

    @Override
    public EventExecutor next() {
        int id = index % eventExecutor.length;
        index++;
        return eventExecutor[id];
    }

    @Override
    public void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor executor : eventExecutor) {
            executor.shutdownGracefully(quietPeriod, timeout, unit);
        }
    }
}
