package com.cc.cetty.channel;

import com.cc.cetty.channel.socket.NioServerSocketChannel;
import com.cc.cetty.pipeline.handler.ChannelInboundHandlerAdapter;
import com.cc.cetty.pipeline.handler.ChannelInitializer;
import com.cc.cetty.pipeline.handler.context.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
@Slf4j
public class ServerChannelInitializer extends ChannelInitializer<NioServerSocketChannel> {
    @Override
    protected void initChannel(NioServerSocketChannel ch) throws Exception {
        log.info("Server channel init");
        ch.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                log.info("New client connection");
                ctx.fireChannelRead(msg);
            }
        });
    }
}
