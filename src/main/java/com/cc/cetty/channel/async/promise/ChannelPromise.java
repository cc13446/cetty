package com.cc.cetty.channel.async.promise;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.listener.GenericFutureListener;
import com.cc.cetty.async.promise.Promise;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public interface ChannelPromise extends ChannelFuture, Promise<Void> {

    /**
     * @return this
     */
    ChannelPromise setSuccess();

    /**
     * @return 是否设置成功
     */
    boolean trySuccess();

    /**
     * @return this
     */
    ChannelPromise unVoid();

    @Override
    Channel channel();

    @Override
    ChannelPromise setSuccess(Void result);

    @Override
    ChannelPromise setFailure(Throwable cause);

    @Override
    ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelPromise removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelPromise sync() throws InterruptedException;

    @Override
    ChannelPromise await() throws InterruptedException;

}
