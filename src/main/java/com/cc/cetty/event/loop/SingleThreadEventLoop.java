package com.cc.cetty.event.loop;

import com.cc.cetty.event.executor.SingleThreadEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.SelectionKey;
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
    public void register(SocketChannel channel, EventLoop eventLoop) {
        eventLoop.execute(() -> {
            try {
                channel.configureBlocking(false);
                channel.register(eventLoop.getSelector(), SelectionKey.OP_READ);
            } catch (IOException e) {
                log.error("注册客户端连接失败");
                throw new RuntimeException(e);
            }
        });
    }
}
