package com.cc.cetty.event.loop;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.event.executor.MultiThreadEventExecutorGroup;

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
    public ChannelFuture register(Channel channel) {
        return next().register(channel);
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return next().register(promise);
    }
}
