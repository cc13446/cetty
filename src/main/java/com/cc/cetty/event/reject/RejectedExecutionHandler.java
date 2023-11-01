package com.cc.cetty.event.reject;

import com.cc.cetty.event.executor.SingleThreadEventExecutor;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public interface RejectedExecutionHandler {

    void rejected(Runnable task, SingleThreadEventExecutor executor);
}

