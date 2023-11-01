package com.cc.cetty.bootstrap

import com.cc.cetty.event.loop.EventLoopGroup
import com.cc.cetty.event.loop.nio.NioEventLoopGroup
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit

/**
 * @author: cc
 * @date: 2023/11/1 
 */
class BootstrapTest extends Specification {

    def "boot strap test"() {
        given:
        def port = 8080
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()
        serverSocketChannel.bind(new InetSocketAddress(port))

        and:
        Bootstrap bootstrap = new Bootstrap()
        EventLoopGroup workerGroup = new NioEventLoopGroup(2)

        and:
        Thread acceptThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                SocketChannel socketChannel = serverSocketChannel.accept()
                bootstrap.group(workerGroup).register(socketChannel)
            }
        })
        acceptThread.start()

        when:
        SocketChannel socketChannel = SocketChannel.open()
        socketChannel.connect(new InetSocketAddress(port))
        socketChannel.write(ByteBuffer.wrap("echo".getBytes()))
        socketChannel.finishConnect()

        and:
        Thread.sleep(5000)
        workerGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS)
        then:
        true
    }
}
