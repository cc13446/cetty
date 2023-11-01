package com.cc.cetty.event.loop;

import com.cc.cetty.event.executor.MultiThreadEventExecutorGroup;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * 多线程事件循环管理器
 *
 * @author: cc
 * @date: 2023/10/31
 */
public abstract class MultiThreadEventLoopGroup extends MultiThreadEventExecutorGroup implements EventLoopGroup {

    /**
     * @param threads 线程数
     */
    public MultiThreadEventLoopGroup(int threads) {
        super(threads);
    }

    /**
     * 由于父类的 EventExecutor 都是通过这里的 newChild 方法创建的，因此时肯定都是 EventLoop
     * @return 下一个时间循环
     */
    @Override
    public EventLoop next() {
        return (EventLoop) super.next();
    }

    @Override
    protected abstract EventLoop newChild();

    @Override
    public void register(SocketChannel channel, EventLoop eventLoop, int ops) {
        next().register(channel, eventLoop, ops);
    }

    @Override
    public void register(ServerSocketChannel channel, EventLoop eventLoop, int ops) {
        next().register(channel, eventLoop, ops);
    }
}
