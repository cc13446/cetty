package com.cc.cetty.event.loop;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.channel.async.promise.DefaultChannelPromise;
import com.cc.cetty.event.executor.SingleThreadEventExecutor;
import com.cc.cetty.event.factory.EventLoopTaskQueueFactory;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * 单线程的事件循环
 *
 * @author: cc
 * @date: 2023/10/31
 */
@Slf4j
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {

    protected SingleThreadEventLoop(Executor executor, EventLoopTaskQueueFactory queueFactory, ThreadFactory threadFactory) {
        super(executor, queueFactory, threadFactory);
    }

    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    public ChannelFuture register(Channel channel) {
        // 在执行任务的时候，channel和promise也是绑定的
        return register(new DefaultChannelPromise(channel, this));
    }

    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        AssertUtils.checkNotNull(promise);
        promise.channel().unsafe().register(this, promise);
        return promise;
    }

}
