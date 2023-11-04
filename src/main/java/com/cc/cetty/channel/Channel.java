package com.cc.cetty.channel;

import com.cc.cetty.attribute.AttributeMap;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.config.ChannelConfig;
import com.cc.cetty.event.loop.EventLoop;

import java.net.SocketAddress;

/**
 * netty 包装的channel
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker {

    /**
     * 原本有单独的类的，这里用long类型替代
     *
     * @return id
     */
    long id();

    /**
     * @return channel 绑定的 event loop
     */
    EventLoop eventLoop();

    /**
     * @return config
     */
    ChannelConfig config();

    /**
     * 当创建的是客户端channel时，parent为serverSocketChannel
     * 如果创建的为服务端channel，parent则为null
     *
     * @return parent
     */
    Channel parent();

    /**
     * @return 是否打开
     */
    boolean isOpen();

    /**
     * @return 是否被注册
     */
    boolean isRegistered();

    /**
     * @return 是否连接
     */
    boolean isActive();

    /**
     * @return 本地地址
     */
    SocketAddress localAddress();

    /**
     * @return 远端地址
     */
    SocketAddress remoteAddress();

    /**
     * @return close future
     */
    ChannelFuture closeFuture();

    /**
     * @return unsafe 类
     */
    Unsafe unsafe();

    @Override
    Channel beginRead();

    @Override
    Channel flush();

    /**
     * 负责原生Channel的一些底层操作
     *
     * @author: cc
     * @date: 2023/11/2
     */
    interface Unsafe {

        SocketAddress localAddress();

        SocketAddress remoteAddress();

        void register(EventLoop eventLoop, ChannelPromise promise);

        void bind(SocketAddress localAddress, ChannelPromise promise);

        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        void disconnect(ChannelPromise promise);

        void close(ChannelPromise promise);

        void closeForcibly();

        void deregister(ChannelPromise promise);

        void beginRead();

        void write(Object msg, ChannelPromise promise);

        void flush();
    }
}
