package com.cc.cetty.bootstrap;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.listener.ChannelFutureListener;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.channel.async.promise.DefaultChannelPromise;
import com.cc.cetty.channel.factory.ChannelFactory;
import com.cc.cetty.channel.factory.ReflectiveChannelFactory;
import com.cc.cetty.event.loop.EventLoopGroup;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * 启动类
 *
 * @author: cc
 * @date: 2023/10/31
 */
@Slf4j
public class Bootstrap<C extends Channel> {

    /**
     * 事件循环管理者
     */
    private EventLoopGroup workerGroup;

    /**
     * channel 工厂
     */
    private volatile ChannelFactory<? extends Channel> channelFactory;

    /**
     * 配置事件循环管理者
     *
     * @param eventLoopGroup group
     * @return this
     */
    public Bootstrap<C> group(EventLoopGroup eventLoopGroup) {
        this.workerGroup = eventLoopGroup;
        return this;
    }

    /**
     * 配置 channel 类型
     *
     * @param channelClass class
     * @return this
     */
    public Bootstrap<C> channel(Class<? extends C> channelClass) {
        this.channelFactory = new ReflectiveChannelFactory<C>(channelClass);
        return this;
    }

    /**
     * 连接
     *
     * @param inetHost ip
     * @param inetPort port
     * @return future
     */
    public ChannelFuture connect(String inetHost, int inetPort) {
        return connect(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 连接
     *
     * @param remoteAddress address
     * @return future
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        AssertUtils.checkNotNull(remoteAddress);
        return doResolveAndConnect(remoteAddress, null);
    }

    /**
     * 连接
     *
     * @param remoteAddress address
     * @param localAddress  address
     * @return future
     */
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        AssertUtils.checkNotNull(remoteAddress);
        return doResolveAndConnect(remoteAddress, localAddress);
    }

    /**
     * 注册、连接
     *
     * @param remoteAddress address
     * @param localAddress  address
     * @return future
     */
    private ChannelFuture doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (regFuture.isDone()) {
            if (!regFuture.isSuccess()) {
                return regFuture;
            }
            // 成功的情况下，直接开始执行绑定端口号的操作,首先创建一个future
            ChannelPromise promise = new DefaultChannelPromise(channel);
            return doResolveAndConnect0(remoteAddress, localAddress, promise);
        } else {
            final ChannelPromise promise = new DefaultChannelPromise(channel);
            regFuture.addListener((ChannelFutureListener) future -> {
                Throwable cause = future.cause();
                if (Objects.nonNull(cause)) {
                    promise.setFailure(cause);
                } else {
                    doResolveAndConnect0(remoteAddress, localAddress, promise);
                }
            });
            return promise;
        }
    }

    /**
     * 连接
     *
     * @param remoteAddress address
     * @param localAddress  address
     * @param promise       promise
     * @return future
     */
    private ChannelFuture doResolveAndConnect0(SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        try {
            doConnect(remoteAddress, localAddress, promise);
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        }
        return promise;
    }

    /**
     * 连接
     *
     * @param remoteAddress  address
     * @param localAddress   address
     * @param connectPromise promise
     */
    private void doConnect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise connectPromise) {
        final Channel channel = connectPromise.channel();
        channel.eventLoop().execute(() ->
                channel.connect(remoteAddress, localAddress, connectPromise)
                        .addListener(ChannelFutureListener.CLOSE_ON_FAILURE));
    }

    /**
     * 初始化并注册
     *
     * @return future
     */
    final ChannelFuture initAndRegister() {
        Channel channel;
        channel = channelFactory.newChannel();
        return workerGroup.next().register(channel);
    }

}
