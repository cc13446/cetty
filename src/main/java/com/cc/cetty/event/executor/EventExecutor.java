package com.cc.cetty.event.executor;

import java.util.concurrent.Executor;

/**
 * 负责执行时间循环的线程执行者
 * @author: cc
 * @date: 2023/10/31
 */
public interface EventExecutor extends EventExecutorGroup, Executor {

    /**
     * @param thread 线程
     * @return 这个线程是否在当前的事件执行器中
     */
    boolean inEventLoop(Thread thread);

}
