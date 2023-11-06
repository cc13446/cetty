package com.cc.cetty.bootstrap;

import com.cc.cetty.attribute.AttributeKey;
import com.cc.cetty.bootstrap.config.ServerBootstrapConfig;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.listener.ChannelFutureListener;
import com.cc.cetty.channel.factory.ChannelFactory;
import com.cc.cetty.config.option.ChannelOption;
import com.cc.cetty.event.loop.EventLoopGroup;
import com.cc.cetty.pipeline.ChannelPipeline;
import com.cc.cetty.pipeline.handler.ChannelHandler;
import com.cc.cetty.pipeline.handler.ChannelInboundHandlerAdapter;
import com.cc.cetty.pipeline.handler.ChannelInitializer;
import com.cc.cetty.pipeline.handler.context.ChannelHandlerContext;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 服务端启动类
 *
 * @author: cc
 * @date: 2023/11/1
 */
@Slf4j
public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap, Channel> {

    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<>();

    private final Map<AttributeKey<?>, Object> childAttrs = new LinkedHashMap<>();

    private final ServerBootstrapConfig config = new ServerBootstrapConfig(this);

    private EventLoopGroup childGroup;

    private volatile ChannelHandler childHandler;

    private volatile ChannelFactory<? extends Channel> channelFactory;

    public ServerBootstrap() {

    }

    public ServerBootstrap(ServerBootstrap bootstrap) {
        super(bootstrap);
        childGroup = bootstrap.childGroup;
        childHandler = bootstrap.childHandler;
        synchronized (bootstrap.childOptions) {
            childOptions.putAll(bootstrap.childOptions);
        }
        synchronized (bootstrap.childAttrs) {
            childAttrs.putAll(bootstrap.childAttrs);
        }
    }

    @Override
    public ServerBootstrap group(EventLoopGroup group) {
        return group(group, group);
    }

    /**
     * 配置事件循环管理者
     *
     * @param parentGroup group
     * @param childGroup  group
     * @return this
     */
    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        super.group(parentGroup);
        AssertUtils.checkNotNull(childGroup, "childGroup");
        if (Objects.nonNull(this.childGroup)) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = childGroup;
        return this;
    }

    /**
     * @param childOption childOption
     * @param value       value
     * @param <T>         T
     * @return this
     */
    public <T> ServerBootstrap childOption(ChannelOption<T> childOption, T value) {
        AssertUtils.checkNotNull(childOption);
        if (Objects.isNull(value)) {
            synchronized (childOptions) {
                childOptions.remove(childOption);
            }
        } else {
            synchronized (childOptions) {
                childOptions.put(childOption, value);
            }
        }
        return this;
    }

    /**
     * @param childKey childKey
     * @param value    value
     * @param <T>      T
     * @return this
     */
    public <T> ServerBootstrap childAttr(AttributeKey<T> childKey, T value) {
        AssertUtils.checkNotNull(childKey);
        if (Objects.isNull(value)) {
            childAttrs.remove(childKey);
        } else {
            childAttrs.put(childKey, value);
        }
        return this;
    }

    /**
     * @param childHandler child handler
     * @return this
     */
    public ServerBootstrap childHandler(ChannelHandler childHandler) {
        this.childHandler = AssertUtils.checkNotNull(childHandler);
        return this;
    }

    @Override
    protected void init(Channel channel) throws Exception {
        final Map<ChannelOption<?>, Object> options = options0();
        synchronized (options) {
            // 把初始化时用户配置的参数全都放到channel的config类中
            setChannelOptions(channel, options);
        }
        final Map<AttributeKey<?>, Object> attrs = attrs0();
        synchronized (attrs) {
            for (Map.Entry<AttributeKey<?>, Object> e : attrs.entrySet()) {
                @SuppressWarnings("unchecked")
                AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
                channel.attr(key).set(e.getValue());
            }
        }
        // 在这里创建channel的ChannelPipeline，也就是ChannelHandler链表
        ChannelPipeline p = channel.pipeline();
        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Map.Entry<ChannelOption<?>, Object>[] currentChildOptions;
        final Map.Entry<AttributeKey<?>, Object>[] currentChildAttrs;
        synchronized (childOptions) {
            currentChildOptions = childOptions.entrySet().toArray(newOptionArray(0));
        }
        synchronized (childAttrs) {
            currentChildAttrs = childAttrs.entrySet().toArray(newAttrArray(0));
        }
        // 这里要给NioServerSocketChannel的ChannelPipeline添加一个handler节点
        // 首先，我们可以看到这个handler是ChannelInitializer类型的，而且添加进去之后，该节点还不是已添加状态
        // 只有当channel注册单线程执行器成功后，该handler的handlerAdded才会被回调，回调的过程中会将handler的状态改为添加完成
        // 然后我们再看看该handler的handlerAdded方法逻辑
        // 我们会发现handlerAdded方法会执行到initChannel方法中，就是下面的重写方法
        // 在重写方法内，会再次拿出NioServerSocketChannel的ChannelPipeline
        // 向ChannelPipeline中添加用户自己向ChannelPipeline中设置的handler
        // 这里用户自己设置的handler应该是一个ChannelInitializer类
        // 这次channel已经注册成功，不必再封装回调链表，可以直接执行callHandlerAdded0方法
        // 这样一来，就直接会回调到用户的ChannelInitializer中的handlerAdded方法，又会执行到重写的initChannel方法中
        // 这时候，才会把用户设置的多个handler真正添加到ChannelPipeline中
        p.addLast(new ChannelInitializer<>() {
            @Override
            public void initChannel(final Channel ch) throws Exception {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (Objects.nonNull(handler)) {
                    pipeline.addLast(handler);
                }
                ch.eventLoop().execute(() -> pipeline.addLast(new ServerBootstrapAcceptor(ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs)));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map.Entry<AttributeKey<?>, Object>[] newAttrArray(int size) {
        return new Map.Entry[size];
    }

    @SuppressWarnings("unchecked")
    private static Map.Entry<ChannelOption<?>, Object>[] newOptionArray(int size) {
        return new Map.Entry[size];
    }

    @Override
    public ServerBootstrap validate() {
        super.validate();
        if (Objects.isNull(childGroup)) {
            log.warn("childGroup is not set. Using parentGroup instead.");
            childGroup = config.group();
        }
        return this;
    }

    /**
     * @return childGroup
     */
    public EventLoopGroup childGroup() {
        return childGroup;
    }

    @Override
    public final ServerBootstrapConfig config() {
        return config;
    }

    private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {

        private final EventLoopGroup childGroup;
        private final ChannelHandler childHandler;
        private final Map.Entry<ChannelOption<?>, Object>[] childOptions;
        private final Map.Entry<AttributeKey<?>, Object>[] childAttrs;
        private final Runnable enableAutoReadTask;

        ServerBootstrapAcceptor(final Channel channel, EventLoopGroup childGroup, ChannelHandler childHandler,
                                Map.Entry<ChannelOption<?>, Object>[] childOptions, Map.Entry<AttributeKey<?>, Object>[] childAttrs) {
            this.childGroup = childGroup;
            this.childHandler = childHandler;
            this.childOptions = childOptions;
            this.childAttrs = childAttrs;
            enableAutoReadTask = () -> channel.config().setAutoRead(true);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // 接收到的其实就是已经实例化好的客户端channel，但是还没有完成初始化
            final Channel child = (Channel) msg;
            // childHandler是服务端为客户端channel设置的handler，这一步是向客户端的channel中添加handler
            child.pipeline().addLast(childHandler);
            // 把用户设置的属性设置到客户端channel中
            setChannelOptions(child, childOptions);
            // NioSocketChannel也是个map，用户存储在map中的参数也要设置进去
            for (Map.Entry<AttributeKey<?>, Object> e: childAttrs) {
                child.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
            // childGroup就是服务端设置的workgroup，这一步就是把接收到的客户端channel绑定到childGroup
            // 中的一个单线程执行器上，绑定成功也会回调客户端channel中handler的相应方法，逻辑都是一样的。
            try {
                childGroup.register(child).addListener((ChannelFutureListener) future -> {
                    // 注册失败，则强制关闭channel
                    if (!future.isSuccess()) {
                        forceClose(child, future.cause());
                    }
                });
            } catch (Throwable t) {
                forceClose(child, t);
            }
        }

        private static void forceClose(Channel child, Throwable t) {
            child.unsafe().closeForcibly();
            log.warn("Failed to register an accepted channel: {}", child, t);
        }
    }

}