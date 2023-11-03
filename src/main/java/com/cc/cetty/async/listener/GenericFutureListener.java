package com.cc.cetty.async.listener;

import com.cc.cetty.async.future.Future;

import java.util.EventListener;

/**
 * 监听器接口
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

