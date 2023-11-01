package com.cc.cetty.bootstrap;

import com.cc.cetty.event.loop.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

/**
 * 服务端启动类
 *
 * @author: cc
 * @date: 2023/11/1
 */
@Slf4j
public class ServerBootstrap {

    private ServerSocketChannel serverSocketChannel;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    /**
     * @param serverSocketChannel serverSocketChannel
     * @return this
     */
    public ServerBootstrap serverSocketChannel(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
        return this;
    }

    /**
     * @param parentGroup 父事件循环组
     * @param childGroup  子事件循环组
     * @return this
     */
    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        this.bossGroup = parentGroup;
        this.workerGroup = childGroup;
        this.bossGroup.setAcceptCallback(socketChannel ->
                this.workerGroup.register(socketChannel, workerGroup.next(), SelectionKey.OP_READ));
        return this;
    }

    /**
     * @param host     ip地址
     * @param inetPort 端口
     */
    public void bind(String host, int inetPort) {
        bind(new InetSocketAddress(host, inetPort));
    }

    /**
     * @param localAddress address
     */
    public void bind(SocketAddress localAddress) {
        this.bossGroup.register(serverSocketChannel, this.bossGroup.next(), SelectionKey.OP_ACCEPT);
        doBind(localAddress);
    }

    /**
     * @param localAddress address
     */
    private void doBind(SocketAddress localAddress) {
        bossGroup.next().execute(() -> {
            try {
                serverSocketChannel.bind(localAddress);
                log.info("服务端channel绑定服务器端口");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        });
    }
}
