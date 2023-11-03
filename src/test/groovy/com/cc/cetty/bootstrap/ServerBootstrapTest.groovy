package com.cc.cetty.bootstrap

import com.cc.cetty.channel.socket.NioServerSocketChannel
import com.cc.cetty.channel.socket.NioSocketChannel
import com.cc.cetty.event.loop.nio.NioEventLoopGroup
import spock.lang.Specification

/**
 * @author: cc
 * @date: 2023/11/1 
 */
class ServerBootstrapTest extends Specification {

    def "server boot strap test"() {
        given:
        def ip = "127.0.0.1"
        def port = 8080


        when:
        ServerBootstrap serverBootstrap = new ServerBootstrap()
        serverBootstrap.channel(NioServerSocketChannel)
                .group(new NioEventLoopGroup(2), new NioEventLoopGroup(4))
                .bind(ip, port).sync()

        and:
        Bootstrap bootstrap = new Bootstrap()
        bootstrap.channel(NioSocketChannel)
                .group(new NioEventLoopGroup(2))
                .connect(ip, port).sync()

        then:
        true
    }
}
