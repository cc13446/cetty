package com.cc.cetty.channel.socket;

import com.cc.cetty.channel.nio.AbstractNioMessageChannel;
import com.cc.cetty.config.socket.ServerSocketChannelConfig;
import com.cc.cetty.config.socket.nio.NioServerSocketChannelConfig;
import com.cc.cetty.event.loop.nio.NioEventLoop;
import com.cc.cetty.utils.SocketUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/3
 */
@Slf4j
public class NioServerSocketChannel extends AbstractNioMessageChannel {

    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static ServerSocketChannel newSocket(SelectorProvider provider) {
        try {
            return provider.openServerSocketChannel();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open a server socket.", e);
        }
    }

    private final ServerSocketChannelConfig config;

    public NioServerSocketChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

    public NioServerSocketChannel(ServerSocketChannel channel) {
        super(null, channel, SelectionKey.OP_ACCEPT);
        config = new NioServerSocketChannelConfig(this, javaChannel().socket());
    }

    @Override
    public ServerSocketChannelConfig config() {
        return config;
    }

    @Override
    public boolean isActive() {
        return isOpen() && javaChannel().socket().isBound();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected SocketAddress localAddress0() {
        return SocketUtils.localSocketAddress(javaChannel().socket());
    }

    @Override
    protected SocketAddress remoteAddress0() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerSocketChannel javaChannel() {
        return (ServerSocketChannel) super.javaChannel();
    }

    @Override
    public NioEventLoop eventLoop() {
        return super.eventLoop();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress, 128);
        if (isActive()) {
            log.info("Bind success");
            doBeginRead();
        } else {
            throw new RuntimeException("Bind fail");
        }
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }


    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SocketChannel ch = SocketUtils.accept(javaChannel());
        try {
            if (Objects.nonNull(ch)) {
                buf.add(new NioSocketChannel(this, ch));
                return 1;
            }
        } catch (Throwable t) {
            log.error("Accept fail", t);
            try {
                ch.close();
            } catch (Throwable t2) {
                throw new RuntimeException("Failed to close a socket.", t2);
            }
        }
        return 0;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

}
