package com.cc.cetty.event.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * 线程创建执行器，netty的执行器中运行的线程都是由这个执行器创建的
 *
 * @author: cc
 * @date: 2023/11/1
 */

@Slf4j
public class ThreadPerTaskExecutor implements Executor {

    private final ThreadFactory threadFactory;

    public ThreadPerTaskExecutor(ThreadFactory threadFactory) {
        if (Objects.isNull(threadFactory)) {
            throw new NullPointerException("threadFactory");
        }
        this.threadFactory = threadFactory;
    }

    @Override
    public void execute(Runnable command) {
        threadFactory.newThread(command).start();
        log.info("创建线程");
    }
}
