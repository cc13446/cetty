package com.cc.cetty.handler.timeout;

import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.listener.ChannelFutureListener;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.event.executor.scheduler.ScheduledFuture;
import com.cc.cetty.handler.timeout.exception.WriteTimeoutException;
import com.cc.cetty.pipeline.handler.ChannelOutboundHandlerAdapter;
import com.cc.cetty.pipeline.handler.context.ChannelHandlerContext;
import com.cc.cetty.utils.AssertUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public class WriteTimeoutHandler extends ChannelOutboundHandlerAdapter {
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final long timeoutNanos;

    private WriteTimeoutTask lastTask;

    private boolean closed;

    public WriteTimeoutHandler(int timeoutSeconds) {
        this(timeoutSeconds, TimeUnit.SECONDS);
    }


    public WriteTimeoutHandler(long timeout, TimeUnit unit) {
        AssertUtils.checkNotNull(unit);
        if (timeout <= 0) {
            timeoutNanos = 0;
        } else {
            timeoutNanos = Math.max(unit.toNanos(timeout), MIN_TIMEOUT_NANOS);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (timeoutNanos > 0) {
            promise = promise.unVoid();
            scheduleTimeout(ctx, promise);
        }
        ctx.write(msg, promise);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        WriteTimeoutTask task = lastTask;
        lastTask = null;
        while (Objects.nonNull(task)) {
            task.scheduledFuture.cancel(false);
            WriteTimeoutTask prev = task.prev;
            task.prev = null;
            task.next = null;
            task = prev;
        }
    }

    private void scheduleTimeout(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        final WriteTimeoutTask task = new WriteTimeoutTask(ctx, promise);
        task.scheduledFuture = ctx.executor().schedule(task, timeoutNanos, TimeUnit.NANOSECONDS);

        if (!task.scheduledFuture.isDone()) {
            addWriteTimeoutTask(task);
            promise.addListener(task);
        }
    }

    private void addWriteTimeoutTask(WriteTimeoutTask task) {
        if (Objects.nonNull(task)) {
            lastTask.next = task;
            task.prev = lastTask;
        }
        lastTask = task;
    }

    private void removeWriteTimeoutTask(WriteTimeoutTask task) {
        if (task == lastTask) {
            assert task.next == null;
            lastTask = lastTask.prev;
            if (lastTask != null) {
                lastTask.next = null;
            }
        } else if (task.prev == null && task.next == null) {
            return;
        } else if (task.prev == null) {
            task.next.prev = null;
        } else {
            task.prev.next = task.next;
            task.next.prev = task.prev;
        }
        task.prev = null;
        task.next = null;
    }


    protected void writeTimedOut(ChannelHandlerContext ctx) {
        if (!closed) {
            ctx.fireExceptionCaught(WriteTimeoutException.INSTANCE);
            ctx.close();
            closed = true;
        }
    }

    private final class WriteTimeoutTask implements Runnable, ChannelFutureListener {

        private final ChannelHandlerContext ctx;
        private final ChannelPromise promise;

        WriteTimeoutTask prev;
        WriteTimeoutTask next;

        ScheduledFuture<?> scheduledFuture;

        WriteTimeoutTask(ChannelHandlerContext ctx, ChannelPromise promise) {
            this.ctx = ctx;
            this.promise = promise;
        }

        @Override
        public void run() {
            if (!promise.isDone()) {
                try {
                    writeTimedOut(ctx);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            }
            removeWriteTimeoutTask(this);
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            scheduledFuture.cancel(false);
            removeWriteTimeoutTask(this);
        }
    }
}
