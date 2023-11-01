package com.cc.cetty.event.loop;

import com.cc.cetty.event.executor.EventExecutorGroup;

import java.nio.channels.SocketChannel;

/**
 * 管理多个 EventLoop
 * <br />
 * 继承 EventExecutorGroup 是因为真正工作的是 EventExecutorGroup
 * EventExecutorGroup 实现的方法 EventLoopGroup 一定要实现
 * @author: cc
 * @date: 2023/10/31
 */
public interface EventLoopGroup extends EventExecutorGroup {

    /**
     * 重新定义返回类型
     * @return 下一个处理事件的循环
     */
    @Override
    EventLoop next();

    /**
     * 将连接注册到事件循环中
     * @param channel channel
     * @param eventLoop eventLoop
     */
    void register(SocketChannel channel, EventLoop eventLoop);
}
