package com.cc.cetty.event.loop;

import com.cc.cetty.event.executor.SingleThreadEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * 单线程的事件循环
 *
 * @author: cc
 * @date: 2023/10/31
 */
@Slf4j
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {
    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    public void register(SocketChannel channel, EventLoop eventLoop, int ops) {
        eventLoop.execute(() -> {
            try {
                register0(channel, eventLoop, ops);
            } catch (IOException e) {
                log.error("注册客户端连接失败");
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 客户端连接注册
     *
     * @param channel   channel
     * @param eventLoop eventLoop
     * @throws IOException IOException
     */
    private void register0(SocketChannel channel, EventLoop eventLoop, int ops) throws IOException {
        channel.configureBlocking(false);
        channel.register(eventLoop.getSelector(), ops);
    }

    @Override
    public void register(ServerSocketChannel channel, EventLoop eventLoop, int ops) {
        eventLoop.execute(() -> {
            try {
                register0(channel, eventLoop, ops);
            } catch (IOException e) {
                log.error("注册服务端绑定socket失败");
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 服务端绑定socket注册
     *
     * @param channel   channel
     * @param eventLoop eventLoop
     * @throws IOException IOException
     */
    private void register0(ServerSocketChannel channel, EventLoop eventLoop, int ops) throws IOException {
        channel.configureBlocking(false);
        channel.register(eventLoop.getSelector(), ops);
    }
}
