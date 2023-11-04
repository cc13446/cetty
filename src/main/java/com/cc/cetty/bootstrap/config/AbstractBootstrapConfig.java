package com.cc.cetty.bootstrap.config;

import com.cc.cetty.bootstrap.AbstractBootstrap;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.factory.ChannelFactory;
import com.cc.cetty.event.loop.EventLoopGroup;
import com.cc.cetty.pipeline.ChannelHandler;
import com.cc.cetty.utils.AssertUtils;

import java.net.SocketAddress;

/**
 * @author: cc
 * @date: 2023/11/4
 */
public abstract class AbstractBootstrapConfig<B extends AbstractBootstrap<B, C>, C extends Channel> {

    protected final B bootstrap;

    protected AbstractBootstrapConfig(B bootstrap) {
        this.bootstrap = AssertUtils.checkNotNull(bootstrap);
    }

    public final SocketAddress localAddress() {
        return bootstrap.localAddress();
    }

    public final ChannelFactory<? extends C> channelFactory() {
        return bootstrap.channelFactory();
    }

    public final ChannelHandler handler() {
        return bootstrap.handler();
    }

    public final EventLoopGroup group() {
        return bootstrap.group();
    }

}
