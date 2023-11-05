package com.cc.cetty.pipeline;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.future.ChannelFuture;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.channel.async.promise.DefaultChannelPromise;
import com.cc.cetty.event.executor.EventExecutor;
import com.cc.cetty.event.executor.EventExecutorGroup;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author: cc
 * @date: 2023/11/05
 **/
@Slf4j
public class DefaultChannelPipeline implements ChannelPipeline {

    private static final String HEAD_NAME = generateName0(HeadContext.class);

    private static final String TAIL_NAME = generateName0(TailContext.class);

    private static final ThreadLocal<Map<Class<?>, String>> nameCaches = ThreadLocal.withInitial(() -> new WeakHashMap<>());

    private final AbstractChannelHandlerContext head;

    private final AbstractChannelHandlerContext tail;

    private final Channel channel;

    private boolean firstRegistration = true;

    /**
     * 向DefaultChannelPipeline中添加handler时，会用到这个链表
     */
    private PendingHandlerCallback pendingHandlerCallbackHead;

    /**
     * Channel是否注册成功，这里指的是是否注册单线程执行器成功
     */
    private boolean registered;

    protected DefaultChannelPipeline(Channel channel) {
        this.channel = AssertUtils.checkNotNull(channel);
        tail = new TailContext(this);
        head = new HeadContext(this);
        head.next = tail;
        tail.prev = head;
    }

    /**
     * 创建一个上下文，也就是创建一个链表中的节点
     *
     * @param group   group
     * @param name    name
     * @param handler handler
     * @return context
     */
    private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
        return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
    }

    /**
     * @param group group
     * @return executor
     */
    private EventExecutor childExecutor(EventExecutorGroup group) {
        if (Objects.isNull(group)) {
            return null;
        }
        return group.next();
    }

    @Override
    public final Channel channel() {
        return channel;
    }

    /**
     * @param newCtx new context
     * @return has called
     */
    private boolean asyncCallHandleAdded(AbstractChannelHandlerContext newCtx) {
        if (!registered) {
            // 设定ChannelHandler的添加状态为ADD_PENDING
            newCtx.setAddPending();
            // 等注册之后再触发handler的added方法，更新状态
            callHandlerCallbackLater(newCtx, true);
            return true;
        }
        EventExecutor executor = newCtx.executor();
        if (!executor.inEventLoop(Thread.currentThread())) {
            callHandlerAddedInEventLoop(newCtx, executor);
            return true;
        }
        return false;
    }

    /**
     * 异步调用added回调
     *
     * @param newCtx   new context
     * @param executor executor
     */
    private void callHandlerAddedInEventLoop(final AbstractChannelHandlerContext newCtx, EventExecutor executor) {
        newCtx.setAddPending();
        executor.execute(() -> callHandlerAdded0(newCtx));
    }

    /**
     * @param name    name
     * @param handler handler
     * @return name
     */
    private String filterName(String name, ChannelHandler handler) {
        if (Objects.isNull(name)) {
            //如果没有指定handler的名字，会生成一个。
            return generateName(handler);
        }
        //到这里说明用户设定了名字，这时候就要判断名字的唯一性
        checkDuplicateName(name);
        return name;
    }

    /**
     * 给ChannelHandler生成名字，并且确保该名字不会重复
     *
     * @param handler hadler
     * @return name
     */
    private String generateName(ChannelHandler handler) {
        //获取缓存着每个handler名字的map
        Map<Class<?>, String> cache = nameCaches.get();
        Class<?> handlerType = handler.getClass();
        String name = cache.get(handlerType);
        if (name == null) {
            //handler没有名字，直接生成一个
            name = generateName0(handlerType);
            cache.put(handlerType, name);
        }
        if (context0(name) != null) {
            //走到这说明名字重复了，就创建新的
            String baseName = name.substring(0, name.length() - 1);
            for (int i = 1; ; i++) {
                String newName = baseName + i;
                if (context0(newName) == null) {
                    name = newName;
                    break;
                }
            }
        }
        return name;
    }

    /**
     * 生成handler的名字
     *
     * @param handlerType tyoe
     * @return name
     */
    private static String generateName0(Class<?> handlerType) {
        return handlerType.getSimpleName() + "#0";
    }

    @Override
    public final ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            // 检查该ChannelHandler是否可以共享，也就是能否被重复添加
            // 能被多个ChannelPipeline共享的handler都会加上@Sharable注解
            checkMultiplicity(handler);
            // 判断handler的名字是否已经存在，如果存在就为该handler创建新的名字
            name = filterName(name, handler);
            // 把ChannelHandler封装在ChannelHandlerContext对象中
            newCtx = newContext(group, name, handler);
            // 执行添加方法
            // 注意仅仅是添加到链表中时，handler还无法真正处理数据，需要等待handler的状态更新为ADD_COMPLETE
            // 在channelHandler的handlerAdded方法被回调后，其状态会更新为ADD_COMPLETE
            addFirst0(newCtx);
            if (asyncCallHandleAdded(newCtx)) {
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    /**
     * 真正添加handler的方法，更改链表中的指针指向
     *
     * @param newCtx new context
     */
    private void addFirst0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
    }

    @Override
    public final ChannelPipeline addLast(String name, ChannelHandler handler) {
        return addLast(null, name, handler);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            checkMultiplicity(handler);
            newCtx = newContext(group, filterName(name, handler), handler);
            addLast0(newCtx);
            if (asyncCallHandleAdded(newCtx)) {
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    /**
     * 真正添加handler的方法，更改链表中的指针指向
     *
     * @param newCtx new context
     */
    private void addLast0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = tail.prev;
        newCtx.prev = prev;
        newCtx.next = tail;
        prev.next = newCtx;
        tail.prev = newCtx;
    }

    @Override
    public final ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        final AbstractChannelHandlerContext ctx;
        synchronized (this) {
            checkMultiplicity(handler);
            name = filterName(name, handler);
            ctx = getContextOrDie(baseName);
            newCtx = newContext(group, name, handler);
            addBefore0(ctx, newCtx);
            if (asyncCallHandleAdded(newCtx)) {
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    private static void addBefore0(AbstractChannelHandlerContext ctx, AbstractChannelHandlerContext newCtx) {
        newCtx.prev = ctx.prev;
        newCtx.next = ctx;
        ctx.prev.next = newCtx;
        ctx.prev = newCtx;
    }


    @Override
    public final ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        return addAfter(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        final AbstractChannelHandlerContext ctx;
        synchronized (this) {
            checkMultiplicity(handler);
            name = filterName(name, handler);
            ctx = getContextOrDie(baseName);
            newCtx = newContext(group, name, handler);
            addAfter0(ctx, newCtx);
            if (asyncCallHandleAdded(newCtx)) {
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }

    private static void addAfter0(AbstractChannelHandlerContext ctx, AbstractChannelHandlerContext newCtx) {
        newCtx.prev = ctx;
        newCtx.next = ctx.next;
        ctx.next.prev = newCtx;
        ctx.next = newCtx;
    }

    public final ChannelPipeline addFirst(ChannelHandler handler) {
        return addFirst(null, handler);
    }

    @Override
    public final ChannelPipeline addFirst(ChannelHandler... handlers) {
        return addFirst(null, handlers);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup executor, ChannelHandler... handlers) {
        AssertUtils.checkNotNull(handlers);
        if (handlers.length == 0 || Objects.isNull(handlers[0])) {
            return this;
        }
        int size;
        for (size = 1; size < handlers.length; size++) {
            if (Objects.isNull(handlers[size])) {
                break;
            }
        }
        for (int i = size - 1; i >= 0; i--) {
            ChannelHandler h = handlers[i];
            addFirst(executor, null, h);
        }
        return this;
    }

    public final ChannelPipeline addLast(ChannelHandler handler) {
        return addLast(null, handler);
    }

    @Override
    public final ChannelPipeline addLast(ChannelHandler... handlers) {
        return addLast(null, handlers);
    }

    @Override
    public final ChannelPipeline addLast(EventExecutorGroup executor, ChannelHandler... handlers) {
        AssertUtils.checkNotNull(handlers);
        for (ChannelHandler h : handlers) {
            if (Objects.isNull(h)) {
                break;
            }
            addLast(executor, null, h);
        }
        return this;
    }

    @Override
    public final ChannelPipeline remove(ChannelHandler handler) {
        remove(getContextOrDie(handler));
        return this;
    }

    @Override
    public final ChannelHandler remove(String name) {
        return remove(getContextOrDie(name)).handler();
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T remove(Class<T> handlerType) {
        return (T) remove(getContextOrDie(handlerType)).handler();
    }

    @SuppressWarnings("unchecked")
    private <T extends ChannelHandler> T removeIfExists(ChannelHandlerContext ctx) {
        if (Objects.isNull(ctx)) {
            return null;
        }
        return (T) remove((AbstractChannelHandlerContext) ctx).handler();
    }

    /**
     * 删除节点
     *
     * @param ctx context
     * @return context removed
     */
    private AbstractChannelHandlerContext remove(final AbstractChannelHandlerContext ctx) {
        //判断不是头节点和尾节点
        assert ctx != head && ctx != tail;
        synchronized (this) {
            //删除链表中对应的ChannelHandlerContext
            remove0(ctx);
            if (!registered) {
                callHandlerCallbackLater(ctx, false);
                return ctx;
            }
            EventExecutor executor = ctx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                executor.execute(() -> callHandlerRemoved0(ctx));
                return ctx;
            }
        }
        callHandlerRemoved0(ctx);
        return ctx;
    }

    /**
     * 删除节点
     *
     * @param ctx context
     */
    private static void remove0(AbstractChannelHandlerContext ctx) {
        AbstractChannelHandlerContext prev = ctx.prev;
        AbstractChannelHandlerContext next = ctx.next;
        prev.next = next;
        next.prev = prev;
    }

    @Override
    public final ChannelHandler removeFirst() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(head.next).handler();
    }

    @Override
    public final ChannelHandler removeLast() {
        if (head.next == tail) {
            throw new NoSuchElementException();
        }
        return remove(tail.prev).handler();
    }

    @Override
    public final ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
        replace(getContextOrDie(oldHandler), newName, newHandler);
        return this;
    }

    @Override
    public final ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
        return replace(getContextOrDie(oldName), newName, newHandler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
        return (T) replace(getContextOrDie(oldHandlerType), newName, newHandler);
    }

    /**
     * @param ctx        context
     * @param newName    name
     * @param newHandler handler
     * @return old handler
     */
    private ChannelHandler replace(final AbstractChannelHandlerContext ctx, String newName, ChannelHandler newHandler) {
        assert ctx != head && ctx != tail;
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            checkMultiplicity(newHandler);
            if (Objects.isNull(newName)) {
                newName = generateName(newHandler);
            } else {
                boolean sameName = ctx.name().equals(newName);
                if (!sameName) {
                    checkDuplicateName(newName);
                }
            }
            newCtx = newContext(ctx.executor, newName, newHandler);
            replace0(ctx, newCtx);
            if (!registered) {
                callHandlerCallbackLater(newCtx, true);
                callHandlerCallbackLater(ctx, false);
                return ctx.handler();
            }
            EventExecutor executor = ctx.executor();
            if (!executor.inEventLoop(Thread.currentThread())) {
                executor.execute(() -> {
                    callHandlerAdded0(newCtx);
                    callHandlerRemoved0(ctx);
                });
                return ctx.handler();
            }
        }
        callHandlerAdded0(newCtx);
        callHandlerRemoved0(ctx);
        return ctx.handler();
    }

    /**
     * 替代节点
     *
     * @param oldCtx old
     * @param newCtx new
     */
    private static void replace0(AbstractChannelHandlerContext oldCtx, AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext prev = oldCtx.prev;
        AbstractChannelHandlerContext next = oldCtx.next;
        newCtx.prev = prev;
        newCtx.next = next;

        prev.next = newCtx;
        next.prev = newCtx;

        oldCtx.prev = newCtx;
        oldCtx.next = newCtx;
    }

    /**
     * 判断该channelHandler是否可以重复添加
     *
     * @param handler handler
     */
    private static void checkMultiplicity(ChannelHandler handler) {
        if (handler instanceof ChannelHandlerAdapter) {
            ChannelHandlerAdapter h = (ChannelHandlerAdapter) handler;
            if (!h.isSharable() && h.added) {
                throw new ChannelPipelineException(h.getClass().getName() + " is not a @Sharable handler, so can't be added or removed multiple times.");
            }
            h.added = true;
        }
    }

    /**
     * 注册后执行added回调
     *
     * @param ctx context
     */
    private void callHandlerAdded0(final AbstractChannelHandlerContext ctx) {
        try {
            ctx.callHandlerAdded();
        } catch (Throwable t) {
            boolean removed = false;
            try {
                remove0(ctx);
                ctx.callHandlerRemoved();
                removed = true;
            } catch (Throwable t2) {
                log.warn("Failed to remove a handler: " + ctx.name(), t2);
            }
            if (removed) {
                fireExceptionCaught(new ChannelPipelineException(ctx.handler().getClass().getName() + ".handlerAdded() has thrown an exception; removed.", t));
            } else {
                fireExceptionCaught(new ChannelPipelineException(ctx.handler().getClass().getName() + ".handlerAdded() has thrown an exception; also failed to remove.", t));
            }
        }
    }

    /**
     * 注册后执行removed回调
     *
     * @param ctx context
     */
    private void callHandlerRemoved0(final AbstractChannelHandlerContext ctx) {
        try {
            ctx.callHandlerRemoved();
        } catch (Throwable t) {
            fireExceptionCaught(new ChannelPipelineException(ctx.handler().getClass().getName() + ".handlerRemoved() has thrown an exception.", t));
        }
    }

    /**
     * channel注册成功的时候，会执行它，然后回调链表中的节点的方法
     */
    final void invokeHandlerAddedIfNeeded() {
        assert channel.eventLoop().inEventLoop(Thread.currentThread());
        // 保证下面的方法只被执行一次
        if (firstRegistration) {
            firstRegistration = false;
            callHandlerAddedForAllHandlers();
        }
    }

    @Override
    public final ChannelHandler first() {
        ChannelHandlerContext first = firstContext();
        if (Objects.isNull(first)) {
            return null;
        }
        return first.handler();
    }

    @Override
    public final ChannelHandlerContext firstContext() {
        AbstractChannelHandlerContext first = head.next;
        if (first == tail) {
            return null;
        }
        return head.next;
    }

    @Override
    public final ChannelHandler last() {
        AbstractChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last.handler();
    }

    @Override
    public final ChannelHandlerContext lastContext() {
        AbstractChannelHandlerContext last = tail.prev;
        if (last == head) {
            return null;
        }
        return last;
    }

    @Override
    public final ChannelHandler get(String name) {
        ChannelHandlerContext ctx = context(name);
        if (Objects.isNull(ctx)) {
            return null;
        } else {
            return ctx.handler();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends ChannelHandler> T get(Class<T> handlerType) {
        ChannelHandlerContext ctx = context(handlerType);
        if (Objects.isNull(ctx)) {
            return null;
        } else {
            return (T) ctx.handler();
        }
    }

    @Override
    public final ChannelHandlerContext context(String name) {
        AssertUtils.checkNotNull(name);
        return context0(name);
    }

    @Override
    public final ChannelHandlerContext context(ChannelHandler handler) {
        AssertUtils.checkNotNull(handler);
        AbstractChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (Objects.isNull(ctx)) {
                return null;
            }
            if (ctx.handler() == handler) {
                return ctx;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public final ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        AssertUtils.checkNotNull(handlerType);
        AbstractChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (Objects.isNull(ctx)) {
                return null;
            }
            if (handlerType.isAssignableFrom(ctx.handler().getClass())) {
                return ctx;
            }
            ctx = ctx.next;
        }
    }

    @Override
    public final List<String> names() {
        List<String> list = new ArrayList<>();
        AbstractChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (Objects.isNull(ctx)) {
                return list;
            }
            list.add(ctx.name());
            ctx = ctx.next;
        }
    }

    @Override
    public final Map<String, ChannelHandler> toMap() {
        Map<String, ChannelHandler> map = new LinkedHashMap<>();
        AbstractChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (ctx == tail) {
                return map;
            }
            map.put(ctx.name(), ctx.handler());
            ctx = ctx.next;
        }
    }

    @Override
    public final Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        return toMap().entrySet().iterator();
    }

    @Override
    public final String toString() {
        StringBuilder buf = new StringBuilder().append(getClass().getSimpleName()).append('{');
        AbstractChannelHandlerContext ctx = head.next;
        for (; ; ) {
            if (ctx == tail) {
                break;
            }

            buf.append('(').append(ctx.name()).append(" = ").append(ctx.handler().getClass().getName()).append(')');

            ctx = ctx.next;
            if (ctx == tail) {
                break;
            }

            buf.append(", ");
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public final ChannelPipeline fireChannelRegistered() {
        AbstractChannelHandlerContext.invokeChannelRegistered(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelUnregistered() {
        AbstractChannelHandlerContext.invokeChannelUnregistered(head);
        return this;
    }

    private synchronized void destroy() {
        destroyUp(head.next, false);
    }

    private void destroyUp(AbstractChannelHandlerContext ctx, boolean inEventLoop) {
        final Thread currentThread = Thread.currentThread();
        final AbstractChannelHandlerContext tail = this.tail;
        for (; ; ) {
            if (ctx == tail) {
                destroyDown(currentThread, tail.prev, inEventLoop);
                break;
            }
            final EventExecutor executor = ctx.executor();
            if (!inEventLoop && !executor.inEventLoop(currentThread)) {
                final AbstractChannelHandlerContext finalCtx = ctx;
                executor.execute(() -> destroyUp(finalCtx, true));
                break;
            }
            ctx = ctx.next;
            inEventLoop = false;
        }
    }

    private void destroyDown(Thread currentThread, AbstractChannelHandlerContext ctx, boolean inEventLoop) {
        final AbstractChannelHandlerContext head = this.head;
        for (; ; ) {
            if (ctx == head) {
                break;
            }
            final EventExecutor executor = ctx.executor();
            if (inEventLoop || executor.inEventLoop(currentThread)) {
                synchronized (this) {
                    remove0(ctx);
                }
                callHandlerRemoved0(ctx);
            } else {
                final AbstractChannelHandlerContext finalCtx = ctx;
                executor.execute(() -> destroyDown(Thread.currentThread(), finalCtx, true));
                break;
            }

            ctx = ctx.prev;
            inEventLoop = false;
        }
    }

    @Override
    public final ChannelPipeline fireChannelActive() {
        AbstractChannelHandlerContext.invokeChannelActive(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelInactive() {
        AbstractChannelHandlerContext.invokeChannelInactive(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireExceptionCaught(Throwable cause) {
        AbstractChannelHandlerContext.invokeExceptionCaught(head, cause);
        return this;
    }

    @Override
    public final ChannelPipeline fireUserEventTriggered(Object event) {
        AbstractChannelHandlerContext.invokeUserEventTriggered(head, event);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelRead(Object msg) {
        AbstractChannelHandlerContext.invokeChannelRead(head, msg);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelReadComplete() {
        AbstractChannelHandlerContext.invokeChannelReadComplete(head);
        return this;
    }

    @Override
    public final ChannelPipeline fireChannelWritabilityChanged() {
        AbstractChannelHandlerContext.invokeChannelWritabilityChanged(head);
        return this;
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress) {
        return tail.bind(localAddress);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress) {
        return tail.connect(remoteAddress);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return tail.connect(remoteAddress, localAddress);
    }

    @Override
    public final ChannelFuture disconnect() {
        return tail.disconnect();
    }

    @Override
    public final ChannelFuture close() {
        return tail.close();
    }

    @Override
    public final ChannelFuture deregister() {
        return tail.deregister();
    }

    @Override
    public final ChannelPipeline flush() {
        tail.flush();
        return this;
    }

    @Override
    public final ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return tail.bind(localAddress, promise);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }

    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public final ChannelFuture disconnect(ChannelPromise promise) {
        return tail.disconnect(promise);
    }

    @Override
    public final ChannelFuture close(ChannelPromise promise) {
        return tail.close(promise);
    }

    @Override
    public final ChannelFuture deregister(final ChannelPromise promise) {
        return tail.deregister(promise);
    }

    @Override
    public final ChannelPipeline beginRead() {
        tail.beginRead();
        return this;
    }

    @Override
    public final ChannelFuture write(Object msg) {
        return tail.write(msg);
    }

    @Override
    public final ChannelFuture write(Object msg, ChannelPromise promise) {
        return tail.write(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return tail.writeAndFlush(msg, promise);
    }

    @Override
    public final ChannelFuture writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }

    @Override
    public final ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel);
    }

    /**
     * 检查名称是否重复了
     *
     * @param name name
     */
    private void checkDuplicateName(String name) {
        if (Objects.nonNull(context0(name))) {
            throw new IllegalArgumentException("Duplicate handler name: " + name);
        }
    }

    /**
     * @param name name
     * @return 同名的context
     */
    private AbstractChannelHandlerContext context0(String name) {
        AbstractChannelHandlerContext context = head.next;
        while (context != tail) {
            if (context.name().equals(name)) {
                return context;
            }
            context = context.next;
        }
        return null;
    }

    /**
     * @param name name
     * @return 获取context 或者报错
     */
    private AbstractChannelHandlerContext getContextOrDie(String name) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(name);
        if (Objects.isNull(ctx)) {
            throw new NoSuchElementException(name);
        } else {
            return ctx;
        }
    }

    /**
     * @param handler handler
     * @return 获取context 或者报错
     */
    private AbstractChannelHandlerContext getContextOrDie(ChannelHandler handler) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(handler);
        if (Objects.isNull(ctx)) {
            throw new NoSuchElementException(handler.getClass().getName());
        } else {
            return ctx;
        }
    }

    /**
     * @param handlerType type
     * @return 获取context 或者报错
     */
    private AbstractChannelHandlerContext getContextOrDie(Class<? extends ChannelHandler> handlerType) {
        AbstractChannelHandlerContext ctx = (AbstractChannelHandlerContext) context(handlerType);
        if (Objects.isNull(ctx)) {
            throw new NoSuchElementException(handlerType.getName());
        } else {
            return ctx;
        }
    }

    /**
     * 调用注册后所有的回调
     */
    private void callHandlerAddedForAllHandlers() {
        //回调任务链表的头节点
        final PendingHandlerCallback pendingHandlerCallbackHead;
        synchronized (this) {
            assert !registered;
            registered = true;
            pendingHandlerCallbackHead = this.pendingHandlerCallbackHead;
            //帮助垃圾回收
            this.pendingHandlerCallbackHead = null;
        }
        PendingHandlerCallback task = pendingHandlerCallbackHead;
        // 挨个执行任务列表中的任务
        while (Objects.nonNull(task)) {
            task.execute();
            task = task.next;
        }
    }

    /**
     * 添加注册后回调
     *
     * @param ctx   context
     * @param added 是否是added事件
     */
    private void callHandlerCallbackLater(AbstractChannelHandlerContext ctx, boolean added) {
        assert !registered;
        //如果是添加节点就创建PendingHandlerAddedTask对象，删除节点就创建PendingHandlerRemovedTask节点
        PendingHandlerCallback task = added ? new PendingHandlerAddedTask(ctx) : new PendingHandlerRemovedTask(ctx);
        PendingHandlerCallback pending = pendingHandlerCallbackHead;
        //如果链表还没有头节点，就把创建的对象设成头节点
        if (Objects.isNull(pending)) {
            pendingHandlerCallbackHead = task;
        } else {
            //如果有头节点了，就把节点依次向后添加
            while (pending.next != null) {
                pending = pending.next;
            }
            pending.next = task;
        }
    }

    protected void onUnhandledInboundException(Throwable cause) {
        log.warn("An exceptionCaught() event was fired, and it reached at the tail of the pipeline. " + "It usually means the last handler in the pipeline did not handle the exception.", cause);
    }

    protected void onUnhandledInboundChannelActive() {
    }


    protected void onUnhandledInboundChannelInactive() {
    }


    protected void onUnhandledInboundMessage(Object msg) {
        log.debug("Discarded inbound message {} that reached at the tail of the pipeline. " + "Please check your pipeline configuration.", msg);
    }

    protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
        onUnhandledInboundMessage(msg);
        log.debug("Discarded message pipeline : {}. Channel : {}.", ctx.pipeline().names(), ctx.channel());
    }


    protected void onUnhandledInboundChannelReadComplete() {
    }


    protected void onUnhandledInboundUserEventTriggered(Object evt) {

    }

    protected void onUnhandledChannelWritabilityChanged() {
    }

    /**
     * 看出尾节点是个入站处理器
     */
    final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {

        TailContext(DefaultChannelPipeline pipeline) {
            super(pipeline, null, TAIL_NAME, TailContext.class);
            setAddComplete();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelInactive();
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            onUnhandledChannelWritabilityChanged();
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            onUnhandledInboundUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            onUnhandledInboundException(cause);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            onUnhandledInboundMessage(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            onUnhandledInboundChannelReadComplete();
        }
    }

    /**
     * 头节点即是出站处理器，又是入站处理器
     */
    final class HeadContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler {

        private final Channel.Unsafe unsafe;

        HeadContext(DefaultChannelPipeline pipeline) {
            super(pipeline, null, HEAD_NAME, HeadContext.class);
            unsafe = pipeline.channel().unsafe();
            //设置channelHandler的状态为ADD_COMPLETE，说明该节点添加之后直接就可以处理数据
            setAddComplete();
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
        }

        @Override
        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
            //调用unsafe的方法，然后就是老样子了，再一路调用到NioServerSocketChannel中
            unsafe.bind(localAddress, promise);
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            unsafe.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.disconnect(promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.close(promise);
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
            unsafe.deregister(promise);
        }

        @Override
        public void beginRead(ChannelHandlerContext ctx) {
            unsafe.beginRead();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            unsafe.write(msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            unsafe.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.fireExceptionCaught(cause);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            invokeHandlerAddedIfNeeded();
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();
            if (!channel.isOpen()) {
                destroy();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.fireChannelActive();
            beginReadIfIsAutoRead();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.fireChannelReadComplete();

            beginReadIfIsAutoRead();
        }

        /**
         * 在这个方法中给channel绑定读事件
         */
        private void beginReadIfIsAutoRead() {
            if (channel.config().isAutoRead()) {
                channel.beginRead();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            ctx.fireChannelWritabilityChanged();
        }
    }

    /**
     * 构成回调链表的节点
     */
    private abstract static class PendingHandlerCallback implements Runnable {
        final AbstractChannelHandlerContext ctx;
        PendingHandlerCallback next;

        PendingHandlerCallback(AbstractChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        abstract void execute();
    }

    private final class PendingHandlerAddedTask extends PendingHandlerCallback {

        PendingHandlerAddedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerAdded0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop(Thread.currentThread())) {
                callHandlerAdded0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    log.warn("Can't invoke handlerAdded() as the EventExecutor {} rejected it, removing handler {}.", executor, ctx.name(), e);
                    remove0(ctx);
                    ctx.setRemoved();
                }
            }
        }
    }

    private final class PendingHandlerRemovedTask extends PendingHandlerCallback {

        PendingHandlerRemovedTask(AbstractChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        public void run() {
            callHandlerRemoved0(ctx);
        }

        @Override
        void execute() {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop(Thread.currentThread())) {
                callHandlerRemoved0(ctx);
            } else {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException e) {
                    log.warn("Can't invoke handlerRemoved() as the EventExecutor {} rejected it," + " removing handler {}.", executor, ctx.name(), e);
                    ctx.setRemoved();
                }
            }
        }
    }
}
