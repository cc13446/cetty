package com.cc.cetty.handler.timeout;

import com.cc.cetty.handler.timeout.exception.ReadTimeoutException;
import com.cc.cetty.pipeline.handler.context.ChannelHandlerContext;

import java.util.concurrent.TimeUnit;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public class ReadTimeoutHandler extends IdleStateHandler {

    private boolean closed;

    public ReadTimeoutHandler(int timeoutSeconds) {
        this(timeoutSeconds, TimeUnit.SECONDS);
    }

    public ReadTimeoutHandler(long timeout, TimeUnit unit) {
        super(timeout, 0, 0, unit);
    }

    @Override
    protected final void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        assert evt.getState() == IdleState.READER_IDLE;
        readTimedOut(ctx);
    }

    protected void readTimedOut(ChannelHandlerContext ctx) {
        if (!closed) {
            ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
            ctx.close();
            closed = true;
        }
    }
}
