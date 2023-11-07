package com.cc.cetty.event.executor.scheduler;

import java.util.concurrent.Future;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface ScheduledFuture<V> extends Future<V>, java.util.concurrent.ScheduledFuture<V> {
}
