package com.cc.cetty.promise.listener;

import com.cc.cetty.promise.future.Future;

import java.util.EventListener;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * 回调函数
     *
     * @param future future
     * @throws Exception Exception
     */
    void operationComplete(F future) throws Exception;
}

