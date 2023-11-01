package com.cc.cetty.bootstrap;

import com.cc.cetty.event.loop.EventLoopGroup;

import java.nio.channels.SocketChannel;

/**
 * 启动类
 *
 * @author: cc
 * @date: 2023/10/31
 */
public class Bootstrap {

    /**
     * 事件循环管理者
     */
    private EventLoopGroup eventLoopGroup;

    /**
     * 配置事件循环管理者
     * @param eventLoopGroup group
     * @return this
     */
    public Bootstrap group(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public void register(SocketChannel channel) {
        eventLoopGroup.register(channel, eventLoopGroup.next());
    }

}
