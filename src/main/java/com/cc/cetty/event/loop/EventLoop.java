package com.cc.cetty.event.loop;

import com.cc.cetty.event.executor.EventExecutor;

import java.nio.channels.Selector;

/**
 * 表示一个事件循环，定义了不同通信方式的事件循环方式
 * 比如 nio 和 bio 就不一样
 * <br />
 * 这里为什么要继承 EventLoopGroup 呢？
 * 因为 EventLoopGroup 只作管理作用，真正实现要在Event Loop中
 * 所以 EventLoopGroup 实现的方法，EventLoop还是要实现的
 *
 * @author: cc
 * @date: 2023/10/31
 */
public interface EventLoop extends EventLoopGroup, EventExecutor {

    /**
     * @return selector
     */
    Selector getSelector();
}
