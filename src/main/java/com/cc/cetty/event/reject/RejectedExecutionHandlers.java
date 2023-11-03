package com.cc.cetty.event.reject;

import java.util.concurrent.RejectedExecutionException;

/**
 * 拒绝执行的处理策略
 * @author: cc
 * @date: 2023/11/1
 */
public class RejectedExecutionHandlers {

    private static final RejectedExecutionHandler REJECT = (task, executor) -> {
        throw new RejectedExecutionException();
    };

    private RejectedExecutionHandlers() {
    }

    public static RejectedExecutionHandler reject() {
        return REJECT;
    }

}
