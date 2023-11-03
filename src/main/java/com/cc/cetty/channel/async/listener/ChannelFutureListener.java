package com.cc.cetty.channel.async.listener;

import com.cc.cetty.async.listener.GenericFutureListener;
import com.cc.cetty.channel.async.future.ChannelFuture;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public interface ChannelFutureListener extends GenericFutureListener<ChannelFuture> {

    ChannelFutureListener CLOSE = future -> future.channel().close();

    ChannelFutureListener CLOSE_ON_FAILURE = future -> {
        if (!future.isSuccess()) {
            future.channel().close();
        }
    };
}
