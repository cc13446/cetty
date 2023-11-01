package com.cc.cetty.event.executor;

import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 管理多个 Event Executor
 * @author: cc
 * @date: 2023/10/31
 */
public interface EventExecutorGroup {

    /**
     * 下一个事件执行器
     * @return executor
     */
    EventExecutor next();

    /**
     * 优雅关闭
     * @param quietPeriod quietPeriod
     * @param timeout timeout
     * @param unit time unit
     */
    void shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * 添加accept消息接受到客户端连接的回调
     */
    void setAcceptCallback(Consumer<SocketChannel> callBack);
}
