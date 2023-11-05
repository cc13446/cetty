package com.cc.cetty.pipeline.invoker;

/**
 * 定义了channel入站时候的方法
 * @author: cc
 * @date: 2023/11/05
 **/
public interface ChannelInboundInvoker {

    ChannelInboundInvoker fireChannelRegistered();

    ChannelInboundInvoker fireChannelUnregistered();

    ChannelInboundInvoker fireChannelActive();

    ChannelInboundInvoker fireChannelInactive();

    ChannelInboundInvoker fireExceptionCaught(Throwable cause);

    ChannelInboundInvoker fireUserEventTriggered(Object event);

    ChannelInboundInvoker fireChannelRead(Object msg);

    ChannelInboundInvoker fireChannelReadComplete();

    ChannelInboundInvoker fireChannelWritabilityChanged();
}

