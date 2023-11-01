package com.cc.cetty.event.executor;

import java.util.concurrent.Executor;

/**
 * 负责执行时间循环的线程执行者
 * @author: cc
 * @date: 2023/10/31
 */
public interface EventExecutor extends EventExecutorGroup, Executor {

}
