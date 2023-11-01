package com.cc.cetty.bootstrap;

import com.cc.cetty.event.loop.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * 启动类
 *
 * @author: cc
 * @date: 2023/10/31
 */
@Slf4j
public class Bootstrap {

    /**
     * 事件循环管理者
     */
    private EventLoopGroup workGroup;

    /**
     * 客户端socket
     */
    private SocketChannel socketChannel;

    /**
     * 配置客户端连接socket
     *
     * @param socketChannel channel
     * @return this
     */
    public Bootstrap socketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        return this;
    }

    /**
     * 配置事件循环管理者
     *
     * @param eventLoopGroup group
     * @return this
     */
    public Bootstrap group(EventLoopGroup eventLoopGroup) {
        this.workGroup = eventLoopGroup;
        return this;
    }

    /**
     * 客户端连接
     *
     * @param host     ip
     * @param inetPort port
     */
    public void connect(String host, int inetPort) {
        connect(new InetSocketAddress(host, inetPort));
    }

    /**
     * @param localAddress address
     */
    public void connect(SocketAddress localAddress) {
        doConnect(localAddress);
    }

    /**
     * @param localAddress address
     */
    private void doConnect(SocketAddress localAddress) {
        this.workGroup.register(socketChannel, this.workGroup.next(), SelectionKey.OP_CONNECT);
        doConnect0(localAddress);
    }

    private void doConnect0(SocketAddress localAddress) {
        this.workGroup.next().execute(() -> {
            try {
                socketChannel.connect(localAddress);
                log.info("客户端channel连接服务器成功");
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        });
    }

}
