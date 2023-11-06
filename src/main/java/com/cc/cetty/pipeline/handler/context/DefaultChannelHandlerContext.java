package com.cc.cetty.pipeline.handler.context;

import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.pipeline.handler.ChannelHandler;
import com.cc.cetty.pipeline.DefaultChannelPipeline;

/**
 * @author: cc
 * @date: 2023/11/05
 **/

public final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {

    private final ChannelHandler handler;

    public DefaultChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor, String name, ChannelHandler handler) {
        super(pipeline, executor, name, handler.getClass());
        this.handler = handler;
    }

    @Override
    public ChannelHandler handler() {
        return handler;
    }
}
