package com.cc.cetty.channel;

import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;

import java.net.SocketAddress;

/**
 * 定义了channel的出站方法
 *
 * @author: cc
 * @date: 2023/11/2
 */
public interface ChannelOutboundInvoker {

    ChannelFuture bind(SocketAddress localAddress);

    ChannelFuture connect(SocketAddress remoteAddress);

    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);

    ChannelFuture disconnect();

    ChannelFuture close();

    ChannelFuture deregister();

    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    ChannelFuture disconnect(ChannelPromise promise);

    ChannelFuture close(ChannelPromise promise);

    ChannelFuture deregister(ChannelPromise promise);

    ChannelOutboundInvoker beginRead();

    ChannelFuture write(Object msg);

    ChannelFuture write(Object msg, ChannelPromise promise);

    ChannelOutboundInvoker flush();

    ChannelFuture writeAndFlush(Object msg, ChannelPromise promise);

    ChannelFuture writeAndFlush(Object msg);

    ChannelPromise newPromise();

    ChannelFuture newSucceededFuture();

    ChannelFuture newFailedFuture(Throwable cause);
}
