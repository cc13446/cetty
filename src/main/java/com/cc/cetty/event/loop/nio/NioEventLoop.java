package com.cc.cetty.event.loop.nio;

import com.cc.cetty.channel.nio.AbstractNioChannel;
import com.cc.cetty.event.factory.EventLoopTaskQueueFactory;
import com.cc.cetty.event.loop.SingleThreadEventLoop;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * nio 事件循环
 *
 * @author: cc
 * @date: 2023/11/1
 */
@Slf4j
public class NioEventLoop extends SingleThreadEventLoop {

    private final SelectorProvider provider;

    private Selector selector;

    public NioEventLoop() {
        this(null, SelectorProvider.provider(), null);
    }

    public NioEventLoop(Executor executor, SelectorProvider selectorProvider, EventLoopTaskQueueFactory queueFactory) {
        super(executor, queueFactory, Thread::new);
        AssertUtils.checkNotNull(selectorProvider);
        this.provider = selectorProvider;
        this.selector = openSelector();
    }

    /**
     * @return selector
     */
    private Selector openSelector() {
        try {
            selector = provider.openSelector();
            return selector;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a new selector", e);
        }
    }

    @Override
    protected void run() {
        while (!Thread.interrupted()) {
            try {
                // 没有事件就阻塞在这里
                select();
                // 如果走到这里，就说明selector不再阻塞了
                processSelectedKeys(selector.selectedKeys());
            } catch (Exception e) {
                log.error("Failed to process a event", e);
            } finally {
                // 执行单线程执行器中的所有任务
                runAllTasks();
            }
        }
    }

    /**
     * select 事件
     *
     * @throws IOException select io 异常
     */
    private void select() throws IOException {
        Selector selector = this.selector;
        while (!Thread.interrupted()) {
            log.info("Event heat beat");
            int selectedKeys = selector.select(3000);
            // 如果有事件或者单线程执行器中有任务待执行，就退出循环
            if (selectedKeys != 0 || hasTask()) {
                break;
            }
        }
    }

    /**
     * 处理所有select key
     *
     * @param selectedKeys keys
     * @throws IOException 处理 select key 发生的异常
     */
    private void processSelectedKeys(Set<SelectionKey> selectedKeys) throws IOException {
        if (selectedKeys.isEmpty()) {
            return;
        }
        Iterator<SelectionKey> i = selectedKeys.iterator();
        while (i.hasNext()) {
            final SelectionKey k = i.next();
            i.remove();

            final Object a = k.attachment();
            if (a instanceof AbstractNioChannel) {
                processSelectedKey(k, (AbstractNioChannel) a);
            }
        }
    }

    /**
     * 处理一个就绪事件
     *
     * @param key key
     * @param ch  channel
     * @throws IOException 处理 select key 发生的异常
     */
    private void processSelectedKey(SelectionKey key, AbstractNioChannel ch) throws IOException {
        try {
            final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
            int ops = key.interestOps();
            if (ops == SelectionKey.OP_CONNECT) {
                // 移除连接事件，否则会一直通知
                ops &= ~SelectionKey.OP_CONNECT;
                key.interestOps(ops);
                // 注册客户端channel感兴趣的读事件
                ch.beginRead();
                // 客户端连接处理
                unsafe.finishConnect();
            }
            if (ops == SelectionKey.OP_READ) {
                unsafe.read();
            }
            if (ops == SelectionKey.OP_ACCEPT) {
                unsafe.read();
            }
        } catch (CancelledKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Selector getSelector() {
        return selector;
    }

}
