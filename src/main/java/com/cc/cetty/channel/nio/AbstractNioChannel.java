package com.cc.cetty.channel.nio;

import com.cc.cetty.channel.AbstractChannel;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.channel.async.promise.ChannelPromise;
import com.cc.cetty.event.loop.EventLoop;
import com.cc.cetty.event.loop.nio.NioEventLoop;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/2
 */
@Slf4j
public abstract class AbstractNioChannel extends AbstractChannel {

    /**
     * serverSocketChannel和SocketChannel的公共父类
     */
    private final SelectableChannel ch;

    /**
     * channel要关注的事件
     */
    protected final int readInterestOp;

    /**
     * channel注册到selector后返回的key
     */
    protected volatile SelectionKey selectionKey;

    /**
     * 是否还有未读取的数据
     */
    protected boolean readPending;


    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            //设置服务端channel为非阻塞模式
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
            throw new RuntimeException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    /**
     * @return javaChannel
     */
    protected SelectableChannel javaChannel() {
        return ch;
    }

    @Override
    public NioEventLoop eventLoop() {
        return (NioEventLoop) super.eventLoop();
    }

    /**
     * @return selectKey
     */
    protected SelectionKey selectionKey() {
        assert Objects.nonNull(selectionKey);
        return selectionKey;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    @Override
    protected void doRegister() throws Exception {
        // 在这里把channel注册到单线程执行器中的selector上
        // 注意这里的第三个参数this
        // 这意味着channel注册的时候把本身，也就是nio类的channel当作附件放到key上了，之后会用到
        selectionKey = javaChannel().register(eventLoop().getSelector(), 0, this);
    }

    @Override
    protected void doBeginRead() throws Exception {
        final SelectionKey selectionKey = this.selectionKey;
        // 检查key是否是有效的
        if (!selectionKey.isValid()) {
            return;
        }
        final int interestOps = selectionKey.interestOps();
        // 如果没有设置过读事件
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    @Override
    protected void doWrite(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * 连接
     *
     * @param remoteAddress remoteAddress
     * @param localAddress  localAddress
     * @return 是否成功
     * @throws Exception Exception
     */
    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    public interface NioUnsafe extends Unsafe {

        SelectableChannel ch();

        void finishConnect();

        void read();

        void forceFlush();
    }

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        @Override
        public final SelectableChannel ch() {
            return javaChannel();
        }

        @Override
        public final void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
            try {
                boolean doConnect = doConnect(remoteAddress, localAddress);
                if (!doConnect) {
                    promise.trySuccess();
                }
            } catch (Exception e) {
                log.error("Connect fail", e);
            }
        }

        @Override
        public final void finishConnect() {
        }

        @Override
        public final void forceFlush() {
        }
    }
}
