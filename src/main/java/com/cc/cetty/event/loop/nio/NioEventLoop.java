package com.cc.cetty.event.loop.nio;

import com.cc.cetty.event.loop.SingleThreadEventLoop;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

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

    private Consumer<SocketChannel> acceptCallBack;

    public NioEventLoop() {
        this.provider = SelectorProvider.provider();
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
            log.error("打开Selector失败");
            throw new RuntimeException("failed to open a new selector", e);
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
                log.error("事件循环处理事件时异常", e);
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
            log.info("事件线程 heat beat");
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
            processSelectedKey(k);
        }
    }

    /**
     * 处理一个就绪事件
     *
     * @param key key
     * @throws IOException 处理 select key 发生的异常
     */
    private void processSelectedKey(SelectionKey key) throws IOException {
        if (key.isConnectable()) {
            SocketChannel channel = (SocketChannel) key.channel();
            if (channel.finishConnect()) {
                channel.register(selector, SelectionKey.OP_READ);
            } else {
                channel.close();
            }
        }
        if (key.isReadable()) {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            int len = channel.read(byteBuffer);
            if (len == -1) {
                log.info("通道关闭");
                channel.close();
                return;
            }
            byte[] bytes = new byte[len];
            byteBuffer.flip();
            byteBuffer.get(bytes);
            log.info("收到对方发送的数据:{}", new String(bytes, StandardCharsets.UTF_8));
        }
        if (key.isAcceptable()) {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();
            if (Objects.nonNull(acceptCallBack)) {
                acceptCallBack.accept(socketChannel);
            }

        }
    }

    @Override
    public Selector getSelector() {
        return selector;
    }

    @Override
    public void setAcceptCallback(Consumer<SocketChannel> callBack) {
        this.acceptCallBack = callBack;
    }
}
