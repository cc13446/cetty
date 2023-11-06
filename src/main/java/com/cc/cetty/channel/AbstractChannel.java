package com.cc.cetty.channel;

import com.cc.cetty.attribute.DefaultAttributeMap;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.channel.async.promise.DefaultChannelPromise;
import com.cc.cetty.event.loop.EventLoop;
import com.cc.cetty.pipeline.ChannelPipeline;
import com.cc.cetty.pipeline.DefaultChannelPipeline;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/2
 */
@Slf4j
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {

    /**
     * 用于关闭 future
     */
    public static final class CloseFuture extends DefaultChannelPromise {

        CloseFuture(AbstractChannel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            return super.trySuccess();
        }
    }

    private final Channel parent;

    private final Unsafe unsafe;

    private final long id;

    private final DefaultChannelPipeline pipeline;

    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile SocketAddress localAddress;

    private volatile SocketAddress remoteAddress;

    private Throwable initialCloseCause;

    private volatile EventLoop eventLoop;

    private volatile boolean registered;

    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        unsafe = newUnsafe();
        id = System.currentTimeMillis();
        pipeline = newChannelPipeline();
    }

    protected AbstractChannel(Channel parent, long id) {
        this.parent = parent;
        this.id = id;
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }

    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this);
    }

    @Override
    public final long id() {
        return id;
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop eventLoop = this.eventLoop;
        AssertUtils.checkNotNull(eventLoop);
        return eventLoop;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public SocketAddress localAddress() {
        SocketAddress localAddress = this.localAddress;
        if (Objects.isNull(localAddress)) {
            try {
                this.localAddress = localAddress = unsafe().localAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (Objects.isNull(remoteAddress)) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        pipeline.bind(localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        pipeline.connect(remoteAddress, localAddress, promise);
        return promise;
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(promise);
    }

    @Override
    public ChannelFuture close() {
        return pipeline.close();
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(promise);
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister();
    }

    @Override
    public Channel beginRead() {
        pipeline.beginRead();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return pipeline.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline.write(msg, promise);
    }

    @Override
    public Channel flush() {
        pipeline.flush();
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline.writeAndFlush(msg);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelPromise newPromise() {
        return pipeline.newPromise();
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    /**
     * @return unsafe 类
     */
    protected abstract AbstractUnsafe newUnsafe();

    /**
     * @param loop 事件循环
     * @return 事件循环是否适配当前通道
     */
    protected abstract boolean isCompatible(EventLoop loop);

    protected abstract class AbstractUnsafe implements Unsafe {

        private boolean neverRegistered = true;

        /**
         * 如果channel注册了，那一定是绑定的事件循环在处理
         */
        private void assertEventLoop() {
            assert !registered || eventLoop.inEventLoop(Thread.currentThread());
        }

        @Override
        public final SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public final SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        @Override
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
            AssertUtils.checkNotNull(eventLoop);
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("Registered to an event loop already"));
                return;
            }
            if (!isCompatible(eventLoop)) {
                promise.setFailure(new IllegalStateException("Incompatible event loop type: " + eventLoop.getClass().getName()));
                return;
            }
            // channel 绑定单线程执行器
            AbstractChannel.this.eventLoop = eventLoop;
            if (eventLoop.inEventLoop(Thread.currentThread())) {
                register0(promise);
            } else {
                try {
                    // 如果调用该方法的线程不是netty的线程，就封装成任务由线程执行器来执行
                    eventLoop.execute(() -> register0(promise));
                } catch (Throwable t) {
                    log.error("Register fail", t);
                    closeForcibly();
                    closeFuture.setClosed();
                    safeSetFailure(promise, t);
                }
            }
        }

        /**
         * 注册
         *
         * @param promise promise
         */
        private void register0(ChannelPromise promise) {
            try {
                if (!promise.setUncancellable() || !ensureOpen(promise)) {
                    return;
                }
                boolean firstRegistration = neverRegistered;
                doRegister();
                neverRegistered = false;
                registered = true;
                // 回调链表中的方法，链表中的每一个节点都会执行它的run方法
                // 在run方法中ChannelPipeline中每一个节点中handler的handlerAdded方法
                // 在执行callHandlerAdded的时候，handler的添加状态更新为ADD_COMPLETE
                pipeline.invokeHandlerAddedIfNeeded();
                safeSetSuccess(promise);
                // channel注册成功后回调每一个handler的channelRegister方法
                pipeline.fireChannelRegistered();
                // 这里默认只注册一次，直接开启读取
                // netty中有可能因为线程池满了会把某些管道的自动读取消，channel也会取消注册
                // 当线程池恢复的时候在打开
                // 这时候就发生两次注册了
                if (isActive()) {
                    if (firstRegistration) {
                        // 如果是被动连接，这个时候可能已经active了
                        // 触发channelActive回调
                        pipeline.fireChannelActive();
                    } else if (config().isAutoRead()) {
                        //在这里有可能无法关注读事件
                        beginRead();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            try {
                doBind(localAddress);
                safeSetSuccess(promise);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public final void disconnect(final ChannelPromise promise) {
        }

        @Override
        public final void close(final ChannelPromise promise) {
        }

        @Override
        public final void closeForcibly() {
            assertEventLoop();
            try {
                doClose();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public final void deregister(final ChannelPromise promise) {
        }

        @Override
        public final void beginRead() {
            assertEventLoop();
            // 如果是服务端的channel，这里仍然可能为false
            // 那么真正注册读事件的时机，就成了绑定端口号成功之后
            if (!isActive()) {
                return;
            }
            try {
                doBeginRead();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public final void write(Object msg, ChannelPromise promise) {
        }

        @Override
        public final void flush() {
        }

        /**
         * @param promise promise
         * @return channel 是否打开
         */
        protected final boolean ensureOpen(ChannelPromise promise) {
            if (isOpen()) {
                return true;
            }
            safeSetFailure(promise, newClosedChannelException(initialCloseCause));
            return false;
        }

        /**
         * 安全设置成功
         *
         * @param promise promise
         */
        protected final void safeSetSuccess(ChannelPromise promise) {
            if (!promise.trySuccess()) {
                System.out.println("Failed to mark a promise as success because it is done already: " + promise);
            }
        }

        /**
         * 安全设置失败
         *
         * @param promise promise
         */
        protected final void safeSetFailure(ChannelPromise promise, Throwable cause) {
            if (!promise.tryFailure(cause)) {
                throw new RuntimeException(cause);
            }
        }
    }

    protected abstract SocketAddress localAddress0();

    protected abstract SocketAddress remoteAddress0();

    protected abstract void doRegister() throws Exception;

    protected abstract void doBind(SocketAddress localAddress) throws Exception;

    protected abstract void doBeginRead() throws Exception;

    protected abstract void doWrite(Object msg) throws Exception;

    protected abstract void doClose() throws Exception;

    /**
     * 新建通道关闭错误
     *
     * @param cause cause
     * @return 通道关闭错误
     */
    private ClosedChannelException newClosedChannelException(Throwable cause) {
        ClosedChannelException exception = new ClosedChannelException();
        if (Objects.nonNull(cause)) {
            exception.initCause(cause);
        }
        return exception;
    }
}
