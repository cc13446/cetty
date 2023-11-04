package com.cc.cetty.bootstrap;

import com.cc.cetty.attribute.AttributeKey;
import com.cc.cetty.bootstrap.config.ServerBootstrapConfig;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.factory.ChannelFactory;
import com.cc.cetty.config.option.ChannelOption;
import com.cc.cetty.event.loop.EventLoopGroup;
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

    private volatile ChannelFactory<? extends Channel> channelFactory;

    public ServerBootstrap() {

    }

    public ServerBootstrap(ServerBootstrap bootstrap) {
        super(bootstrap);
        childGroup = bootstrap.childGroup;
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
        // ChannelPipeline p = channel.pipeline();
        // p.addLast(config.handler());
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

}