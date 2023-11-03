package com.cc.cetty.channel.async.promise;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.listener.GenericFutureListener;
import com.cc.cetty.async.promise.DefaultPromise;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.utils.AssertUtils;

import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/2
 */
public class DefaultChannelPromise extends DefaultPromise<Void> implements ChannelPromise {

    private final Channel channel;

    public DefaultChannelPromise(Channel channel) {
        AssertUtils.checkNotNull(channel);
        this.channel = channel;
    }

    public DefaultChannelPromise(Channel channel, EventExecutor executor) {
        super(executor);
        AssertUtils.checkNotNull(channel);
        this.channel = channel;
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (Objects.isNull(e)) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPromise setSuccess() {
        return setSuccess(null);
    }

    @Override
    public ChannelPromise setSuccess(Void result) {
        super.setSuccess(result);
        return this;
    }

    @Override
    public boolean trySuccess() {
        return trySuccess(null);
    }

    @Override
    public ChannelPromise setFailure(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    @Override
    public ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public ChannelPromise sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public ChannelPromise await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    protected void checkDeadLock() {
        if (channel().isRegistered()) {
            super.checkDeadLock();
        }
    }

    @Override
    public ChannelPromise unVoid() {
        return this;
    }

    @Override
    public boolean isVoid() {
        return false;
    }
}
