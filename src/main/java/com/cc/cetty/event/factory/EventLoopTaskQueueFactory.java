package com.cc.cetty.event.factory;

import java.util.Queue;

/**
 * 创建任务队列的工厂
 *
 * @author: cc
 * @date: 2023/11/1
 */
public interface EventLoopTaskQueueFactory {

    Queue<Runnable> newTaskQueue(int maxCapacity);
}
