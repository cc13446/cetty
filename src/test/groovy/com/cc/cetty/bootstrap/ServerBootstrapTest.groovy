package com.cc.cetty.bootstrap


import com.cc.cetty.event.loop.nio.NioEventLoopGroup
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
/**
 * @author: cc
 * @date: 2023/11/1 
 */
class ServerBootstrapTest extends Specification {

    def "server boot strap test"() {
        given:
        def ip = "127.0.0.1"
        def port = 8080
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()
        ServerBootstrap serverBootstrap = new ServerBootstrap()
        serverBootstrap.group(new NioEventLoopGroup(2), new NioEventLoopGroup(4))
                .serverSocketChannel(serverSocketChannel)
                .bind(ip, port)

        Thread.sleep(1000)

        and:
        SocketChannel socketChannel = SocketChannel.open()
        Bootstrap bootstrap = new Bootstrap()
        bootstrap.group(new NioEventLoopGroup(2))
                .socketChannel(socketChannel)
                .connect(ip, port)

        and:
        Thread writeThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                Thread.sleep(1000)
                if (socketChannel.isConnected()) {
                    socketChannel.write(ByteBuffer.wrap("echo".getBytes()))
                    break
                }
            }
        })
        writeThread.start()

        when:
        writeThread.join()
        Thread.sleep(5000)

        then:
        true
    }
}
