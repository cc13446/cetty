package com.cc.cetty.pipeline;

import com.cc.cetty.pipeline.annotation.Sharable;

/**
 * @author: cc
 * @date: 2023/11/05
 **/
public abstract class ChannelHandlerAdapter implements ChannelHandler {

    boolean added;

    protected void ensureNotSharable() {
        if (isSharable()) {
            throw new IllegalStateException("ChannelHandler " + getClass().getName() + " is not allowed to be shared");
        }
    }


    public boolean isSharable() {
        Class<?> clazz = getClass();
        return clazz.isAnnotationPresent(Sharable.class);
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }


    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
