package com.cc.cetty.bootstrap;

import com.cc.cetty.attribute.AttributeKey;
import com.cc.cetty.bootstrap.config.AbstractBootstrapConfig;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.listener.ChannelFutureListener;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.channel.async.promise.DefaultChannelPromise;
import com.cc.cetty.channel.factory.ChannelFactory;
import com.cc.cetty.channel.factory.ReflectiveChannelFactory;
import com.cc.cetty.config.option.ChannelOption;
import com.cc.cetty.event.loop.EventLoopGroup;
import com.cc.cetty.pipeline.handler.ChannelHandler;
import com.cc.cetty.utils.AssertUtils;
import com.cc.cetty.utils.SocketUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @param <B>
 * @param <C>
 */
@Slf4j
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> {

    /**
     * ServerSocketChannel -> bossGroup
     * SocketChannel -> workGroup
     */
    protected volatile EventLoopGroup group;

    /**
     * channel 工厂
     */
    private volatile ChannelFactory<? extends C> channelFactory;

    /**
     * 本地地址
     */
    private volatile SocketAddress localAddress;

    /**
     * option
     */
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();

    /**
     * attr
     */
    private final Map<AttributeKey<?>, Object> attrs = new LinkedHashMap<>();

    /**
     * 用户设置的channel handler
     */
    private volatile ChannelHandler handler;

    AbstractBootstrap() {

    }

    AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        group = bootstrap.group;
        channelFactory = bootstrap.channelFactory;
        handler = bootstrap.handler;
        localAddress = bootstrap.localAddress;
        synchronized (bootstrap.options) {
            options.putAll(bootstrap.options);
        }
        synchronized (bootstrap.attrs) {
            attrs.putAll(bootstrap.attrs);
        }
    }

    /**
     * @return this
     */
    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /**
     * @param group group
     * @return this
     */
    public B group(EventLoopGroup group) {
        AssertUtils.checkNotNull(group);
        if (Objects.nonNull(this.group)) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    /**
     * 配置channel
     *
     * @param channelClass channel class
     * @return this
     */
    public B channel(Class<? extends C> channelClass) {
        return channelFactory(new ReflectiveChannelFactory<>(AssertUtils.checkNotNull(channelClass)));
    }

    /**
     * @param channelFactory channel factory
     * @return this
     */
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        AssertUtils.checkNotNull(channelFactory);
        if (Objects.nonNull(this.channelFactory)) {
            throw new IllegalStateException("channelFactory set already");
        }
        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * @param localAddress local address
     * @return this
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * @param inetPort port
     * @return this
     */
    public B localAddress(int inetPort) {
        return localAddress(new InetSocketAddress(inetPort));
    }

    /**
     * @param inetHost ip
     * @param inetPort host
     * @return this
     */
    public B localAddress(String inetHost, int inetPort) {
        return localAddress(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * @param inetHost ip
     * @param inetPort host
     * @return this
     */
    public B localAddress(InetAddress inetHost, int inetPort) {
        return localAddress(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * 把用户定义的channel参数存入map中
     *
     * @param option option
     * @param value  value
     * @param <T>    T
     * @return this
     */
    public <T> B option(ChannelOption<T> option, T value) {
        AssertUtils.checkNotNull(option);
        if (Objects.isNull(value)) {
            synchronized (options) {
                options.remove(option);
            }
        } else {
            synchronized (options) {
                options.put(option, value);
            }
        }
        return self();
    }


    /**
     * 把attribute存入map中
     *
     * @param key   key
     * @param value value
     * @param <T>   T
     * @return this
     */
    public <T> B attr(AttributeKey<T> key, T value) {
        AssertUtils.checkNotNull(key);
        if (Objects.isNull(value)) {
            synchronized (attrs) {
                attrs.remove(key);
            }
        } else {
            synchronized (attrs) {
                attrs.put(key, value);
            }
        }
        return self();
    }

    /**
     * @param handler handler
     * @return this
     */
    public B handler(ChannelHandler handler) {
        this.handler = AssertUtils.checkNotNull(handler);
        return self();
    }

    /**
     * @return 检验有效
     */
    public B validate() {
        if (Objects.isNull(group)) {
            throw new IllegalStateException("group not set");
        }
        if (Objects.isNull(channelFactory)) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    /**
     * 将channel注册到单线程执行器上的方法
     *
     * @return this
     */
    public ChannelFuture register() {
        validate();
        return initAndRegister();
    }

    /**
     * @return future
     */
    public ChannelFuture bind() {
        validate();
        SocketAddress localAddress = this.localAddress;
        if (Objects.isNull(localAddress)) {
            throw new IllegalStateException("localAddress not set");
        }
        return doBind(localAddress);
    }

    /**
     * @param inetPort port
     * @return future
     */
    public ChannelFuture bind(int inetPort) {
        return bind(new InetSocketAddress(inetPort));
    }

    /**
     * @param inetHost ip
     * @param inetPort port
     * @return future
     */
    public ChannelFuture bind(String inetHost, int inetPort) {
        return bind(SocketUtils.socketAddress(inetHost, inetPort));
    }

    /**
     * @param inetHost ip
     * @param inetPort port
     * @return future
     */
    public ChannelFuture bind(InetAddress inetHost, int inetPort) {
        return bind(new InetSocketAddress(inetHost, inetPort));
    }

    /**
     * @param localAddress address
     * @return future
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        return doBind(AssertUtils.checkNotNull(localAddress));
    }

    /**
     * @param localAddress address
     * @return future
     */
    private ChannelFuture doBind(final SocketAddress localAddress) {
        // 初始化channel，并注册到selector上
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();
        if (Objects.nonNull(regFuture.cause())) {
            return regFuture;
        }
        if (regFuture.isDone()) {
            ChannelPromise promise = new DefaultChannelPromise(channel);
            doBind0(regFuture, channel, localAddress, promise);
            return promise;
        } else {
            // 注册还没完成，通过异步的方式完成bind
            final DefaultChannelPromise promise = new DefaultChannelPromise(channel);
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
     * 初始化并且把channel注册到单线程执行器上
     *
     * @return future
     */
    protected final ChannelFuture initAndRegister() {
        Channel channel = null;
        try {
            channel = channelFactory.newChannel();
            init(channel);
        } catch (Throwable t) {
            if (Objects.nonNull(channel)) {
                channel.unsafe().closeForcibly();
                return new DefaultChannelPromise(channel, channel.eventLoop()).setFailure(t);
            }
        }
        ChannelFuture regFuture = config().group().register(channel);
        if (Objects.nonNull(regFuture.cause()) && Objects.nonNull(channel)) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }
        return regFuture;
    }

    /**
     * 初始化channel的方法，这里定义为抽象的，意味着客户端channel和服务端channel实现的方法各不相同
     *
     * @param channel channel
     * @throws Exception exception
     */
    protected abstract void init(Channel channel) throws Exception;

    /**
     * @return group
     */
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * @return options
     */
    protected final Map<ChannelOption<?>, Object> options0() {
        return options;
    }

    /**
     * @return attrs
     */
    protected final Map<AttributeKey<?>, Object> attrs0() {
        return attrs;
    }

    /**
     * @return address
     */
    public final SocketAddress localAddress() {
        return localAddress;
    }

    /**
     * @return channel factory
     */
    public final ChannelFactory<? extends C> channelFactory() {
        return channelFactory;
    }

    /**
     * @return hander
     */
    public final ChannelHandler handler() {
        return handler;
    }


    /**
     * @return config
     */
    public abstract AbstractBootstrapConfig<B, C> config();

    /**
     * 设置channel的option
     *
     * @param channel channel
     * @param options options
     */
    public static void setChannelOptions(Channel channel, Map<ChannelOption<?>, Object> options) {
        for (Map.Entry<ChannelOption<?>, Object> e : options.entrySet()) {
            setChannelOption(channel, e.getKey(), e.getValue());
        }
    }

    /**
     * 设置channel的option
     *
     * @param channel channel
     * @param options options
     */
    public static void setChannelOptions(Channel channel, Map.Entry<ChannelOption<?>, Object>[] options) {
        for (Map.Entry<ChannelOption<?>, Object> e : options) {
            setChannelOption(channel, e.getKey(), e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(Channel channel, ChannelOption<?> option, Object value) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                log.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            log.warn("Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }
}
