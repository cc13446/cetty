package com.cc.cetty.pipeline;

import com.cc.cetty.channel.async.promise.ChannelPromise;

import java.net.SocketAddress;

/**
 * @author: cc
 * @date: 2023/11/05
 **/
public interface ChannelOutboundHandler extends ChannelHandler{

    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception;

    void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception;

    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    void beginRead(ChannelHandlerContext ctx) throws Exception;

    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception;

    void flush(ChannelHandlerContext ctx) throws Exception;

}
