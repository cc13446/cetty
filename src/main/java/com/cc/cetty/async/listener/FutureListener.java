package com.cc.cetty.async.listener;

import com.cc.cetty.async.future.Future;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public interface FutureListener<V> extends GenericFutureListener<Future<V>> {
}

