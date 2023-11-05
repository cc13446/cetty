package com.cc.cetty.pipeline;

import com.cc.cetty.attribute.Attribute;
import com.cc.cetty.attribute.AttributeKey;
import com.cc.cetty.channel.AbstractChannel;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.channel.async.promise.DefaultChannelPromise;
import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static com.cc.cetty.pipeline.ChannelHandlerMask.*;

/**
 * DefaultChannelPipeline类中定义了调用方法
 * 然后调用到AbstractChannelHandlerContext类中
 * 在AbstractChannelHandlerContext中，交给真正做事的ChannelHandler去执行
 *
 * @author: cc
 * @date: 2023/11/05
 **/
@Slf4j
public abstract class AbstractChannelHandlerContext implements ChannelHandlerContext {

    private static final AtomicIntegerFieldUpdater<AbstractChannelHandlerContext> HANDLER_STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(AbstractChannelHandlerContext.class, "handlerState");

    /**
     * handler的添加状态，只有ADD_COMPLETE状态的handler可以处理事件
     */
    private static final int INIT = 0;
    private static final int ADD_PENDING = 1;
    private static final int ADD_COMPLETE = 2;
    private static final int REMOVE_COMPLETE = 3;

    volatile AbstractChannelHandlerContext next;

    volatile AbstractChannelHandlerContext prev;

    private final DefaultChannelPipeline pipeline;

    private final String name;

    /**
     * false：ChannelHandler状态为ADD_PENDING时，也可以响应pipeline中的事件
     * true：ChannelHandler的状态为ADD_COMPLETE时，才能响应pipeline中的事件
     */
    private final boolean ordered;

    /**
     * 表明该handler对哪个事件感兴趣的
     */
    private final int executionMask;

    final EventExecutor executor;

    private volatile int handlerState = INIT;

    AbstractChannelHandlerContext(DefaultChannelPipeline pipeline, EventExecutor executor, String name, Class<? extends ChannelHandler> handlerClass) {
        this.name = AssertUtils.checkNotNull(name);
        this.pipeline = pipeline;
        this.executor = executor;
        // channelHandlerContext中保存channelHandler的执行条件掩码（是什么类型的ChannelHandler,对什么事件感兴趣）
        this.executionMask = mask(handlerClass);
        // Its ordered if it is driven by the EventLoop or the given Executor is an instanceof OrderedEventExecutor.
        ordered = executor == null;
    }

    @Override
    public Channel channel() {
        return pipeline.channel();
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public EventExecutor executor() {
        if (Objects.isNull(executor)) {
            return channel().eventLoop();
        } else {
            return executor;
        }
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * 找到下一个对register事件感兴趣的ChannelHandler
     * register事件就是handler中的channelRegistered方法
     * 只要该方法被重写，就意味着该ChannelHandler对register事件感兴趣
     */
    @Override
    public ChannelHandlerContext fireChannelRegistered() {
        invokeChannelRegistered(findContextInbound(MASK_CHANNEL_REGISTERED));
        return this;
    }

    /**
     * 执行该handler中的ChannelRegistered方法
     * 从该方法可以看出，一旦channel绑定了单线程执行器
     * 那么关于该channel的一切，都要由单线程执行器来执行和处理
     * 如果当前调用方法的线程不是单线程执行器的线程，那就把要进行的动作封装为异步任务提交给执行器
     */
    static void invokeChannelRegistered(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelRegistered();
        } else {
            executor.execute(next::invokeChannelRegistered);
        }
    }

    /**
     * 真正执行handler中的ChannelRegistered方法
     */
    private void invokeChannelRegistered() {
        // 接下来会一直看见invokeHandler这个方法，这个方法就是判断ChannelHandler在链表中的状态
        // 只要是ADD_COMPLETE，才会返回true，方法才能继续向下运行
        // 如果返回false，那就进入else分支，会跳过该节点，寻找下一个可以处理数据的节点
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelRegistered(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelRegistered();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelUnregistered() {
        invokeChannelUnregistered(findContextInbound(MASK_CHANNEL_UNREGISTERED));
        return this;
    }

    static void invokeChannelUnregistered(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelUnregistered();
        } else {
            executor.execute(next::invokeChannelUnregistered);
        }
    }

    private void invokeChannelUnregistered() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelUnregistered(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelUnregistered();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelActive() {
        invokeChannelActive(findContextInbound(MASK_CHANNEL_ACTIVE));
        return this;
    }

    static void invokeChannelActive(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelActive();
        } else {
            executor.execute(next::invokeChannelActive);
        }
    }

    private void invokeChannelActive() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelActive(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelActive();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelInactive() {
        invokeChannelInactive(findContextInbound(MASK_CHANNEL_INACTIVE));
        return this;
    }

    static void invokeChannelInactive(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelInactive();
        } else {
            executor.execute(next::invokeChannelInactive);
        }
    }

    private void invokeChannelInactive() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelInactive(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelInactive();
        }
    }

    @Override
    public ChannelHandlerContext fireExceptionCaught(final Throwable cause) {
        invokeExceptionCaught(findContextInbound(MASK_EXCEPTION_CAUGHT), cause);
        return this;
    }

    static void invokeExceptionCaught(final AbstractChannelHandlerContext next, final Throwable cause) {
        AssertUtils.checkNotNull(cause);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeExceptionCaught(cause);
        } else {
            try {
                executor.execute(() -> next.invokeExceptionCaught(cause));
            } catch (Throwable t) {
                log.warn("Failed to submit an exceptionCaught() event.", t);
            }
        }
    }

    private void invokeExceptionCaught(final Throwable cause) {
        if (invokeHandler()) {
            try {
                handler().exceptionCaught(this, cause);
            } catch (Throwable error) {
                log.error("When handle exception throw new exception", cause);
            }
        } else {
            fireExceptionCaught(cause);
        }
    }

    @Override
    public ChannelHandlerContext fireUserEventTriggered(final Object event) {
        invokeUserEventTriggered(findContextInbound(MASK_USER_EVENT_TRIGGERED), event);
        return this;
    }

    static void invokeUserEventTriggered(final AbstractChannelHandlerContext next, final Object event) {
        AssertUtils.checkNotNull(event);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeUserEventTriggered(event);
        } else {
            executor.execute(() -> next.invokeUserEventTriggered(event));
        }
    }

    private void invokeUserEventTriggered(Object event) {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).userEventTriggered(this, event);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireUserEventTriggered(event);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelRead(final Object msg) {
        invokeChannelRead(findContextInbound(MASK_CHANNEL_READ), msg);
        return this;
    }

    static void invokeChannelRead(final AbstractChannelHandlerContext next, Object msg) {
        final Object m = msg;
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelRead(m);
        } else {
            executor.execute(() -> next.invokeChannelRead(m));
        }
    }

    private void invokeChannelRead(Object msg) {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelRead(this, msg);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelRead(msg);
        }
    }

    @Override
    public ChannelHandlerContext fireChannelReadComplete() {
        invokeChannelReadComplete(findContextInbound(MASK_CHANNEL_READ_COMPLETE));
        return this;
    }

    static void invokeChannelReadComplete(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelReadComplete();
        }

    }

    private void invokeChannelReadComplete() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelReadComplete(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelReadComplete();
        }
    }

    @Override
    public ChannelHandlerContext fireChannelWritabilityChanged() {
        invokeChannelWritabilityChanged(findContextInbound(MASK_CHANNEL_WRITABILITY_CHANGED));
        return this;
    }

    static void invokeChannelWritabilityChanged(final AbstractChannelHandlerContext next) {
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeChannelWritabilityChanged();
        }
    }

    private void invokeChannelWritabilityChanged() {
        if (invokeHandler()) {
            try {
                ((ChannelInboundHandler) handler()).channelWritabilityChanged(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            fireChannelWritabilityChanged();
        }
    }

    /**
     * 每当我调用一个方法时，比如说就是服务端channel的绑定端口号的bind方法，调用链路会先从AbstractChannel类中开始
     * 但是，channel拥有ChannelPipeline链表，链表中有一系列的处理器，所以调用链就会跑到ChannelPipeline中
     * 然后从ChannelPipeline又跑到每一个ChannelHandler中
     * 经过这些ChannelHandler的处理，调用链又会跑到channel的内部类Unsafe中
     * 再经过一系列的调用，最后来到NioServerSocketChannel中，执行真正的doBind方法
     */
    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    @Override
    public ChannelFuture disconnect() {
        return disconnect(newPromise());
    }

    @Override
    public ChannelFuture close() {
        return close(newPromise());
    }

    @Override
    public ChannelFuture deregister() {
        return deregister(newPromise());
    }

    /**
     * 调用链路从这里跑到了ChannelHandler中
     */
    @Override
    public ChannelFuture bind(final SocketAddress localAddress, final ChannelPromise promise) {
        AssertUtils.checkNotNull(localAddress);
        //找到对bind事件感兴趣的handler
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_BIND);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeBind(localAddress, promise);
        } else {
            safeExecute(executor, () -> next.invokeBind(localAddress, promise), promise, null);
        }
        return promise;
    }

    private void invokeBind(SocketAddress localAddress, ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                // 每次都要调用handler()方法来获得handler，但是接口中的handler方法是在哪里实现的呢？
                // 在DefaultChannelHandlerContext类中，这也提醒着我们，我们创建的context节点是DefaultChannelHandlerContext节点。
                ((ChannelOutboundHandler) handler()).bind(this, localAddress, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            bind(localAddress, promise);
        }
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return connect(remoteAddress, null, promise);
    }

    @Override
    public ChannelFuture connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        AssertUtils.checkNotNull(remoteAddress);
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeConnect(remoteAddress, localAddress, promise);
        } else {
            safeExecute(executor, () -> next.invokeConnect(remoteAddress, localAddress, promise), promise, null);
        }
        return promise;
    }

    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).connect(this, remoteAddress, localAddress, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            connect(remoteAddress, localAddress, promise);
        }
    }

    @Override
    public ChannelFuture disconnect(final ChannelPromise promise) {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DISCONNECT);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeDisconnect(promise);
        } else {
            safeExecute(executor, () -> next.invokeDisconnect(promise), promise, null);
        }
        return promise;
    }

    private void invokeDisconnect(ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).disconnect(this, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            disconnect(promise);
        }
    }

    @Override
    public ChannelFuture close(final ChannelPromise promise) {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_CLOSE);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeClose(promise);
        } else {
            safeExecute(executor, () -> next.invokeClose(promise), promise, null);
        }

        return promise;
    }

    private void invokeClose(ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).close(this, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            close(promise);
        }
    }

    @Override
    public ChannelFuture deregister(final ChannelPromise promise) {
        if (isNotValidPromise(promise, false)) {
            return promise;
        }
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_DEREGISTER);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeDeregister(promise);
        } else {
            safeExecute(executor, () -> next.invokeDeregister(promise), promise, null);
        }

        return promise;
    }

    private void invokeDeregister(ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).deregister(this, promise);
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            deregister(promise);
        }
    }

    @Override
    public ChannelHandlerContext beginRead() {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_READ);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeRead();
        }
        return this;
    }

    private void invokeRead() {
        if (invokeHandler()) {
            try {
                ((ChannelOutboundHandler) handler()).beginRead(this);
            } catch (Throwable t) {
                notifyHandlerException(t);
            }
        } else {
            beginRead();
        }
    }

    @Override
    public ChannelFuture write(Object msg) {
        return write(msg, newPromise());
    }

    @Override
    public ChannelFuture write(final Object msg, final ChannelPromise promise) {
        write(msg, false, promise);

        return promise;
    }

    private void invokeWrite(Object msg, ChannelPromise promise) {
        if (invokeHandler()) {
            invokeWrite0(msg, promise);
        } else {
            write(msg, promise);
        }
    }

    private void invokeWrite0(Object msg, ChannelPromise promise) {
        try {
            ((ChannelOutboundHandler) handler()).write(this, msg, promise);
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }

    @Override
    public ChannelHandlerContext flush() {
        final AbstractChannelHandlerContext next = findContextOutbound(MASK_FLUSH);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            next.invokeFlush();
        }
        return this;
    }

    private void invokeFlush() {
        if (invokeHandler()) {
            //发送缓冲区的数据
            invokeFlush0();
        } else {
            flush();
        }
    }

    private void invokeFlush0() {
        try {
            ((ChannelOutboundHandler) handler()).flush(this);
        } catch (Throwable t) {
            notifyHandlerException(t);
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        write(msg, true, promise);
        return promise;
    }

    private void invokeWriteAndFlush(Object msg, ChannelPromise promise) {
        if (invokeHandler()) {
            invokeWrite0(msg, promise);
            invokeFlush0();
        } else {
            writeAndFlush(msg, promise);
        }
    }

    private void write(Object msg, boolean flush, ChannelPromise promise) {
        AssertUtils.checkNotNull(msg);
        final AbstractChannelHandlerContext next = findContextOutbound(flush ? (MASK_WRITE | MASK_FLUSH) : MASK_WRITE);
        final Object m = msg;
        EventExecutor executor = next.executor();
        if (executor.inEventLoop(Thread.currentThread())) {
            if (flush) {
                next.invokeWriteAndFlush(m, promise);
            } else {
                next.invokeWrite(m, promise);
            }
        } else {
            executor.execute(() -> next.invokeWriteAndFlush(m, promise));
        }
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    private static void notifyOutboundHandlerException(Throwable cause, ChannelPromise promise) {
        promise.tryFailure(cause);
    }

    private void notifyHandlerException(Throwable cause) {
        if (inExceptionCaught(cause)) {
            log.warn("An exception was thrown by a user handler " + "while handling an exceptionCaught event", cause);
            return;
        }

        invokeExceptionCaught(cause);
    }

    private static boolean inExceptionCaught(Throwable cause) {
        do {
            StackTraceElement[] trace = cause.getStackTrace();
            if (trace != null) {
                for (StackTraceElement t : trace) {
                    if (t == null) {
                        break;
                    }
                    if ("exceptionCaught".equals(t.getMethodName())) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        } while (cause != null);

        return false;
    }


    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel(), executor());
    }

    private boolean isNotValidPromise(ChannelPromise promise, boolean allowVoidPromise) {
        if (promise == null) {
            throw new NullPointerException("promise");
        }
        if (promise.isDone()) {
            if (promise.isCancelled()) {
                return true;
            }
            throw new IllegalArgumentException("promise already done: " + promise);
        }
        if (promise.channel() != channel()) {
            throw new IllegalArgumentException(String.format("promise.channel does not match: %s (expected: %s)", promise.channel(), channel()));
        }
        if (promise.getClass() == DefaultChannelPromise.class) {
            return false;
        }
        if (promise instanceof AbstractChannel.CloseFuture) {
            throw new IllegalArgumentException(AbstractChannel.CloseFuture.class.getSimpleName() + " not allowed in a pipeline");
        }
        return false;
    }

    private AbstractChannelHandlerContext findContextInbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        do {
            //为什么获取后一个？因为是入站处理器，数据从前往后传输
            ctx = ctx.next;
        } while ((ctx.executionMask & mask) == 0);
        return ctx;
    }

    private AbstractChannelHandlerContext findContextOutbound(int mask) {
        AbstractChannelHandlerContext ctx = this;
        do {
            //为什么获取前一个？因为是出站处理器，数据从后往前传输
            ctx = ctx.prev;
        } while ((ctx.executionMask & mask) == 0);
        return ctx;
    }

    /**
     * 把链表中的ChannelHandler的状态设置为删除完成
     */
    final void setRemoved() {
        handlerState = REMOVE_COMPLETE;
    }

    /**
     * 把链表中的ChannelHandler的状态设置为添加完成
     */
    final boolean setAddComplete() {
        for (; ; ) {
            int oldState = handlerState;
            if (oldState == REMOVE_COMPLETE) {
                return false;
            }
            if (HANDLER_STATE_UPDATER.compareAndSet(this, oldState, ADD_COMPLETE)) {
                return true;
            }
        }
    }

    /**
     * 把链表中的ChannelHandler的状态设置为等待添加
     */
    final void setAddPending() {
        boolean updated = HANDLER_STATE_UPDATER.compareAndSet(this, INIT, ADD_PENDING);
        assert updated;
    }

    /**
     * 在该方法中，ChannelHandler的添加状态将变为添加完成，然后ChannelHandler调用它的 handlerAdded方法
     */
    final void callHandlerAdded() throws Exception {
        //在这里改变channelHandler的状态
        if (setAddComplete()) {
            handler().handlerAdded(this);
        }
    }

    /**
     * 回调链表中节点的handlerRemoved方法，该方法在ChannelPipeline中有节点被删除时被调用。
     */
    final void callHandlerRemoved() throws Exception {
        try {
            if (handlerState == ADD_COMPLETE) {
                handler().handlerRemoved(this);
            }
        } finally {
            setRemoved();
        }
    }

    /**
     * 判断ChannelPipeline中节点的状态是否为ADD_COMPLETE，只有状态为ADD_COMPLETE时，handler才可以处理数据
     */
    private boolean invokeHandler() {
        int handlerState = this.handlerState;
        return handlerState == ADD_COMPLETE || (!ordered && handlerState == ADD_PENDING);
    }

    @Override
    public boolean isRemoved() {
        return handlerState == REMOVE_COMPLETE;
    }

    /**
     * 该方法就可以得到用户存储在channel这个map中的数据，每一个handler都可以得到
     */
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return channel().attr(key);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel().hasAttr(key);
    }

    private static boolean safeExecute(EventExecutor executor, Runnable runnable, ChannelPromise promise, Object msg) {
        try {
            executor.execute(runnable);
            return true;
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return false;
        }
    }

    @Override
    public String toString() {
        return ChannelHandlerContext.class.getSimpleName() + '(' + name + ", " + channel() + ')';
    }

}
