package com.cc.cetty.pipeline;

import com.cc.cetty.attribute.Attribute;
import com.cc.cetty.attribute.AttributeKey;
import com.cc.cetty.attribute.AttributeMap;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.pipeline.invoker.ChannelInboundInvoker;
import com.cc.cetty.pipeline.invoker.ChannelOutboundInvoker;

/**
 * 封装ChannelHandler，和每一个handler的上下文信息
 * 一个个ChannelHandlerContext对象，构成了ChannelPipeline中责任链的链表
 * 链表的每一个节点都是ChannelHandlerContext对象，每一个ChannelHandlerContext对象里都有一个handler
 *
 * @author: cc
 * @date: 2023/11/05
 **/
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * @return channel
     */
    Channel channel();

    /**
     * @return pipe line
     */
    ChannelPipeline pipeline();

    /**
     * @return executor
     */
    EventExecutor executor();

    /**
     * @return name
     */
    String name();

    /**
     * @return handler
     */
    ChannelHandler handler();

    /**
     * @return 是否被移除
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext beginRead();

    @Override
    ChannelHandlerContext flush();

    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}