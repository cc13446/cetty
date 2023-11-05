package com.cc.cetty.pipeline;

import com.cc.cetty.event.executor.EventExecutor;

/**
 * @author: cc
 * @date: 2023/11/05
 **/

public final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    private final ChannelHandler handler;

    DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, handler.getClass());
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
}
