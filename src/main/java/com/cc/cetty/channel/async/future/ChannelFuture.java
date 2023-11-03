package com.cc.cetty.channel.async.future;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.listener.GenericFutureListener;
import com.cc.cetty.channel.Channel;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * @return channel
     */
    Channel channel();

    /**
     * @return 是否为空
     */
    boolean isVoid();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture await() throws InterruptedException;

}