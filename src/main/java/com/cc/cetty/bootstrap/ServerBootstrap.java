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
import com.cc.cetty.utils.SocketUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

/**
 * 服务端启动类
 *
 * @author: cc
 * @date: 2023/11/1
 */
@Slf4j
public class ServerBootstrap<C extends Channel> {

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private volatile ChannelFactory<? extends Channel> channelFactory;

    /**
     * 配置事件循环管理者
     *
     * @param parentGroup group
     * @param childGroup  group
     * @return this
     */
    public ServerBootstrap<C> group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        this.bossGroup = parentGroup;
        this.workerGroup = childGroup;
        return this;
    }


    /**
     * 配置 channel 类型
     *
     * @param channelClass class
     * @return this
     */
    public ServerBootstrap<C> channel(Class<? extends C> channelClass) {
        this.channelFactory = new ReflectiveChannelFactory<C>(channelClass);
        return this;
    }

    /**
     * 绑定
     *
     * @param inetPort port
     * @return future
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * 绑定
     *
     * @param inetHost ip
     * @param inetPort port
     * @return future
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * 绑定
     *
     * @param inetHost ip
     * @param inetPort port
     * @return future
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 绑定
     *
     * @param localAddress address
     * @return future
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        return doBind(AssertUtils.checkNotNull(localAddress));
    }

    /**
     * 注册并绑定
     *
     * @param localAddress address
     * @return future
     */
    private ChannelFuture doBind(SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister();
        Channel channel = regFuture.channel();
        if (regFuture.isDone()) {
            ChannelPromise promise = new DefaultChannelPromise(channel);
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            final ChannelPromise promise = new DefaultChannelPromise(channel);
            regFuture.addListener((ChannelFutureListener) future -> {
                Throwable cause = future.cause();
                if (Objects.nonNull(cause)) {
                    promise.setFailure(cause);
                } else {
                    doBind0(regFuture, channel, localAddress, promise);
                }
            });
            return promise;
        }
    }

    /**
     * 绑定
     *
     * @param regFuture    future
     * @param channel      channel
     * @param localAddress address
     * @param promise      promise
     */
    private void doBind0(final ChannelFuture regFuture, final Channel channel, final SocketAddress localAddress, final ChannelPromise promise) {
        channel.eventLoop().execute(() -> {
            if (regFuture.isSuccess()) {
                channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                promise.setFailure(regFuture.cause());
            }
        });
    }

    /**
     * 初始化并注册
     *
     * @return future
     */
    final ChannelFuture initAndRegister() {
        Channel channel;
        channel = channelFactory.newChannel();
        return bossGroup.next().register(channel);
    }

}