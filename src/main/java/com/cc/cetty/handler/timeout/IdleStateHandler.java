package com.cc.cetty.handler.timeout;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.listener.ChannelFutureListener;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.event.executor.scheduler.ScheduledFuture;
import com.cc.cetty.pipeline.handler.ChannelDuplexHandler;
import com.cc.cetty.pipeline.handler.context.ChannelHandlerContext;
import com.cc.cetty.utils.AssertUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;


/**
 * @author: cc
 * @date: 2023/11/06
 **/
public class IdleStateHandler extends ChannelDuplexHandler {

    /**
     * 这个属性会跟用户设定的读写超时时间做对比，然后取较大的值
     */
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final ChannelFutureListener writeListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            lastWriteTime = ticksInNanos();
            firstWriterIdleEvent = firstAllIdleEvent = true;
        }
    };

    /**
     * 观察出站缓冲区的情况，默认是false，不观察
     */
    private final boolean observeOutput;

    /**
     * 设定的读空闲时间
     */
    private final long readerIdleTimeNanos;

    /**
     * 设定的写空闲时间
     */
    private final long writerIdleTimeNanos;

    /**
     * 所有的空闲时间，实际上就是读空闲和写空闲都用它来表示
     */
    private final long allIdleTimeNanos;

    /**
     * 读空闲的定时任务
     */
    private ScheduledFuture<?> readerIdleTimeout;

    /**
     * 最后一次的读时间
     */
    private long lastReadTime;

    /**
     * 第一次触发读取超时事件，默认为true
     */
    private boolean firstReaderIdleEvent = true;

    /**
     * 写事件的定时任务
     */
    private ScheduledFuture<?> writerIdleTimeout;

    /**
     * 最后一次的写时间
     */
    private long lastWriteTime;

    /**
     * 第一次触发写取超时事件，默认为true
     */
    private boolean firstWriterIdleEvent = true;

    private ScheduledFuture<?> allIdleTimeout;

    private boolean firstAllIdleEvent = true;

    /**
     * 该处理器的状态，0意味着无状态，1代表初始化，2代表销毁
     */
    private byte state;

    /**
     * 是否正在读取数据的标志
     */
    private boolean reading;

    private long lastChangeCheckTimeStamp;
    private int lastMessageHashCode;
    private long lastPendingWriteBytes;
    private long lastFlushProgress;


    public IdleStateHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        this(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds, TimeUnit.SECONDS);
    }


    public IdleStateHandler(long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit unit) {
        this(false, readerIdleTime, writerIdleTime, allIdleTime, unit);
    }

    public IdleStateHandler(boolean observeOutput, long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit unit) {

        AssertUtils.checkNotNull(unit);
        this.observeOutput = observeOutput;
        if (readerIdleTime <= 0) {
            readerIdleTimeNanos = 0;
        } else {
            readerIdleTimeNanos = Math.max(unit.toNanos(readerIdleTime), MIN_TIMEOUT_NANOS);
        }
        if (writerIdleTime <= 0) {
            writerIdleTimeNanos = 0;
        } else {
            writerIdleTimeNanos = Math.max(unit.toNanos(writerIdleTime), MIN_TIMEOUT_NANOS);
        }
        if (allIdleTime <= 0) {
            allIdleTimeNanos = 0;
        } else {
            allIdleTimeNanos = Math.max(unit.toNanos(allIdleTime), MIN_TIMEOUT_NANOS);
        }
    }

    public long getReaderIdleTimeInMillis() {
        return TimeUnit.NANOSECONDS.toMillis(readerIdleTimeNanos);
    }


    public long getWriterIdleTimeInMillis() {
        return TimeUnit.NANOSECONDS.toMillis(writerIdleTimeNanos);
    }


    public long getAllIdleTimeInMillis() {
        return TimeUnit.NANOSECONDS.toMillis(allIdleTimeNanos);
    }

    /**
     * 该方法会在channel注册到selector上时被首先回调 然后执行其中的initialize方法。在该方法中会判断是否要初始化。
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            initialize(ctx);
        }
        // 如果这里没有初始化，那就等active回调的时候
    }

    /**
     * 该处理器被删除时，执行销毁方法
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        destroy();
    }

    /**
     * 这里还会判断是否要进行一次初始化
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        //channel还未被激活，不会执行初始化方法。
        if (ctx.channel().isActive()) {
            initialize(ctx);
        }
        super.channelRegistered(ctx);
    }

    /**
     * 这里会进行初始化处理器的操作
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        initialize(ctx);
        super.channelActive(ctx);
    }

    /**
     * channel不活跃了，就执行销毁方法
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }

    /**
     * 这个方法被回调了，就意味着收到数据了
     * 接收数据处理数据一定是NioSocketChannel的工作
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 做一个判断，看是否设置了读空闲时间
        if (readerIdleTimeNanos > 0 || allIdleTimeNanos > 0) {
            // 把读数据的标志设置为true，说明正在接收数据
            reading = true;
            // 读超时时间是否第一次触发设置为true
            firstReaderIdleEvent = firstAllIdleEvent = true;
        }
        ctx.fireChannelRead(msg);
    }


    /**
     * 这个方法会在channelRead方法被回调之后再回调，并且该方法只被回调一次
     * 而channelRead方法可能会被回调多次，因为数据很多，可能要多次才能读取完整
     * 在读取了所有的数据之后，channelReadComplete才会被回调，意思是读取完全了
     * 在该方法中，会把最后一次读数据的事件重制为当前时间，并且把正在读数据的标志置为false，因为已经接收完数据了
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if ((readerIdleTimeNanos > 0 || allIdleTimeNanos > 0) && reading) {
            lastReadTime = ticksInNanos();
            reading = false;
        }
        ctx.fireChannelReadComplete();
    }


    /**
     * write事件的方法，IdleStateHandler本身也是个出站处理器
     * 当发送数据的时候，经过该处理器，会在该方法内添加一个发送事件成功后的监听器
     * 监听器内执行的逻辑和之前读事件的逻辑类似，就不再重复了
     * 当然有一点不同，因为是发送数据，所以自然是要把最后一次发送数据的事件置为当前时间
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (writerIdleTimeNanos > 0 || allIdleTimeNanos > 0) {
            ctx.write(msg, promise.unVoid()).addListener(writeListener);
        } else {
            ctx.write(msg, promise);
        }
    }

    /**
     * 该方法的作用实际上就是初始化该处理器。把state赋上值，把最后一次读写时间设置为当前时间等等
     */
    private void initialize(ChannelHandlerContext ctx) {
        // 如果初始化过就直接退出
        switch (state) {
            case 1:
            case 2:
                return;
        }
        // 状态赋值
        state = 1;
        // 初始化lastMessageHashCode，lastPendingWriteBytes，lastFlushProgress这三个属性
        initOutputChanged(ctx);
        // 最后一次读写时间设置成当前时间
        lastReadTime = lastWriteTime = ticksInNanos();
        // 设置读超时定时任务，这里面的重点在ReaderIdleTimeoutTask
        if (readerIdleTimeNanos > 0) {
            readerIdleTimeout = schedule(ctx, new ReaderIdleTimeoutTask(ctx), readerIdleTimeNanos, TimeUnit.NANOSECONDS);
        }
        // 设置写超时定时任务
        if (writerIdleTimeNanos > 0) {
            writerIdleTimeout = schedule(ctx, new WriterIdleTimeoutTask(ctx), writerIdleTimeNanos, TimeUnit.NANOSECONDS);
        }
        // 设置读或写超时定时任务
        if (allIdleTimeNanos > 0) {
            allIdleTimeout = schedule(ctx, new AllIdleTimeoutTask(ctx), allIdleTimeNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * @return 当前ns
     */
    long ticksInNanos() {
        return System.nanoTime();
    }

    /**
     * 设置定时任务
     */
    ScheduledFuture<?> schedule(ChannelHandlerContext ctx, Runnable task, long delay, TimeUnit unit) {
        return ctx.executor().schedule(task, delay, unit);
    }

    /**
     * 销毁方法，实际上就是把定时任务置为null
     */
    private void destroy() {
        state = 2;

        if (Objects.nonNull(readerIdleTimeout)) {
            readerIdleTimeout.cancel(false);
            readerIdleTimeout = null;
        }
        if (Objects.nonNull(writerIdleTimeout)) {
            writerIdleTimeout.cancel(false);
            writerIdleTimeout = null;
        }
        if (Objects.nonNull(allIdleTimeout)) {
            allIdleTimeout.cancel(false);
            allIdleTimeout = null;
        }
    }

    /**
     * 该方法就是把读写空闲事件向管道的节点上传递，并且被相应节点的UserEventTriggered方法处理
     */
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * 根据不同的情况，创建不同的异常事件，或者是读事件，或者是写事件，或者是读写事件
     */
    protected IdleStateEvent newIdleStateEvent(IdleState state, boolean first) {
        switch (state) {
            case ALL_IDLE:
                return first ? IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT : IdleStateEvent.ALL_IDLE_STATE_EVENT;
            case READER_IDLE:
                return first ? IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT : IdleStateEvent.READER_IDLE_STATE_EVENT;
            case WRITER_IDLE:
                return first ? IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT : IdleStateEvent.WRITER_IDLE_STATE_EVENT;
            default:
                throw new IllegalArgumentException("Unhandled: state=" + state + ", first=" + first);
        }
    }

    /**
     * 该方法的作用是给三个属性赋值，这三个属性很重要，会配合observeOutput一起使用
     */
    private void initOutputChanged(ChannelHandlerContext ctx) {
//        if (observeOutput) {
//            Channel channel = ctx.channel();
//            Channel.Unsafe unsafe = channel.unsafe();
//            ChannelOutboundBuffer buf = unsafe.outboundBuffer();
//
//            if (buf != null) {
//                lastMessageHashCode = System.identityHashCode(buf.current());
//                lastPendingWriteBytes = buf.totalPendingWriteBytes();
//                lastFlushProgress = buf.currentProgress();
//            }
//        }
    }


    /**
     * 在这里会用到observeOutput属性，观察缓冲区有没有发生变化，该方法主要和写事件相关。
     */
    private boolean hasOutputChanged(ChannelHandlerContext ctx, boolean first) {
        //observeOutput默认为false，所以该分支就不会进入。会直接返回false，这里涉及到和后面相关的知识，我们还没引入，所以暂时不做讲解。等后面
        //讲了新的知识，大家可以再回来看这里的逻辑，其实很简单的。
//        if (observeOutput) {
//            //如果记录最后一次检查的时间和最后一次写事件的时间不相等，说明肯定发生了变化
//            if (lastChangeCheckTimeStamp != lastWriteTime) {
//                lastChangeCheckTimeStamp = lastWriteTime;
//                if (!first) {
//                    return true;
//                }
//            }
//            Channel channel = ctx.channel();
//            Channel.Unsafe unsafe = channel.unsafe();
//            //得到发送缓冲区
//            ChannelOutboundBuffer buf = unsafe.outboundBuffer();
//            //出站缓冲区中的数据不为空，继续向下运行
//            if (buf != null) {
//                //得到待发送对象的哈希值
//                int messageHashCode = System.identityHashCode(buf.current());
//                //得到ChannelOutboundBuffer也就是发送缓冲区中待发送数据的总大小
//                long pendingWriteBytes = buf.totalPendingWriteBytes();
//                //这里是判断代发送对象的hash值和最后一次发送对象的hahs值是否一致，如果不一致则说明缓冲区是有变化的，也就说明实际上正在
//                //执行写事件。后面是判断发送数据的大小和最后一次发送数据的总大小是否一致
//                if (messageHashCode != lastMessageHashCode || pendingWriteBytes != lastPendingWriteBytes) {
//                    lastMessageHashCode = messageHashCode;
//                    lastPendingWriteBytes = pendingWriteBytes;
//                    if (!first) {
//                        return true;
//                    }
//                }
//                long flushProgress = buf.currentProgress();
//                if (flushProgress != lastFlushProgress) {
//                    lastFlushProgress = flushProgress;
//                    if (!first) {
//                        return true;
//                    }
//                }
//            }
//        }
        return false;
    }

    /**
     * 抽象的定时任务类，定义了最基本的调用逻辑，要被其各个子类实现
     */
    private abstract static class AbstractIdleTask implements Runnable {

        private final ChannelHandlerContext ctx;

        AbstractIdleTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }
            run(ctx);
        }

        protected abstract void run(ChannelHandlerContext ctx);
    }

    /**
     * 读事件的定时任务类
     */
    private final class ReaderIdleTimeoutTask extends AbstractIdleTask {

        /**
         * 构造方法
         */
        ReaderIdleTimeoutTask(ChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        protected void run(ChannelHandlerContext ctx) {
            // 读空闲时间赋值
            long nextDelay = readerIdleTimeNanos;
            // 如果此时reading还是true，则说明当前的数据还未读取完整，但肯定是有读事件到来了，
            // 那就不必计算读空闲是否超时，这时候nextDelay肯定是大于0的，直接走到最下面的分支刷新定时任务即可。
            if (!reading) {
                // 当前时间减去最后一次读的时间，让读空闲时间减去该时间
                nextDelay -= ticksInNanos() - lastReadTime;
            }
            // 如果结果小于0，说明已经超时读了
            if (nextDelay <= 0) {
                // 刷新定时任务，再过readerIdleTimeNanos时间执行定时任务
                readerIdleTimeout = schedule(ctx, this, readerIdleTimeNanos, TimeUnit.NANOSECONDS);
                // 判断是否是第一次触发超时事件
                boolean first = firstReaderIdleEvent;
                // 把是否第一次触发超时事件置为false
                firstReaderIdleEvent = false;
                try {
                    // 创建读空闲异常事件
                    IdleStateEvent event = newIdleStateEvent(IdleState.READER_IDLE, first);
                    // 把该时间传递到管道中
                    channelIdle(ctx, event);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                // 定时任务的时间为剩余的读空闲时间
                readerIdleTimeout = schedule(ctx, this, nextDelay, TimeUnit.NANOSECONDS);
            }
        }
    }

    /**
     * 写事件的定时任务，逻辑和上面的方法类似，就不再详细注释了，重点关注一下hasOutputChanged方法即可
     */
    private final class WriterIdleTimeoutTask extends AbstractIdleTask {

        WriterIdleTimeoutTask(ChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        protected void run(ChannelHandlerContext ctx) {
            long lastWriteTime = IdleStateHandler.this.lastWriteTime;
            long nextDelay = writerIdleTimeNanos - (ticksInNanos() - lastWriteTime);
            if (nextDelay <= 0) {
                writerIdleTimeout = schedule(ctx, this, writerIdleTimeNanos, TimeUnit.NANOSECONDS);
                // 如果是第一次触发写空闲事件，这里就是true，传入hasOutputChanged方法的参数也为true
                boolean first = firstWriterIdleEvent;
                // 置为false，如果再次发生写空闲时间，就不是第一次了
                firstWriterIdleEvent = false;
                try {
                    // 观察写缓冲区是否发生了变化，如果发生了变化，说明正在进行写事件，有数据要写出，
                    // 那就直接返回即可，不必发布写空闲异常事件，但默认的是不观察缓冲区变化，
                    // observeOutput默认为false，所以该方法在这里不会生效，会直接返回false，不会执行return，而是继续向下运行。
                    if (hasOutputChanged(ctx, first)) {
                        return;
                    }
                    IdleStateEvent event = newIdleStateEvent(IdleState.WRITER_IDLE, first);
                    channelIdle(ctx, event);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                writerIdleTimeout = schedule(ctx, this, nextDelay, TimeUnit.NANOSECONDS);
            }
        }
    }

    /**
     * 读或写事件的定时任务
     */
    private final class AllIdleTimeoutTask extends AbstractIdleTask {

        AllIdleTimeoutTask(ChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        protected void run(ChannelHandlerContext ctx) {

            long nextDelay = allIdleTimeNanos;
            if (!reading) {
                nextDelay -= ticksInNanos() - Math.max(lastReadTime, lastWriteTime);
            }
            if (nextDelay <= 0) {
                allIdleTimeout = schedule(ctx, this, allIdleTimeNanos, TimeUnit.NANOSECONDS);

                boolean first = firstAllIdleEvent;
                firstAllIdleEvent = false;

                try {
                    if (hasOutputChanged(ctx, first)) {
                        return;
                    }
                    IdleStateEvent event = newIdleStateEvent(IdleState.ALL_IDLE, first);
                    channelIdle(ctx, event);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                allIdleTimeout = schedule(ctx, this, nextDelay, TimeUnit.NANOSECONDS);
            }
        }
    }
}

