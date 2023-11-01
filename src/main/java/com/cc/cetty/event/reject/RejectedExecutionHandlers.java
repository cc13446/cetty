package com.cc.cetty.event.reject;

import java.util.concurrent.RejectedExecutionException;

/**
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
