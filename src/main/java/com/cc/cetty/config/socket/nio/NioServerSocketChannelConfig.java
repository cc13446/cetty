package com.cc.cetty.config.socket.nio;

import com.cc.cetty.channel.socket.NioServerSocketChannel;
import com.cc.cetty.config.option.ChannelOption;
import com.cc.cetty.config.option.NioChannelOption;
import com.cc.cetty.config.socket.DefaultServerSocketChannelConfig;

import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public final class NioServerSocketChannelConfig extends DefaultServerSocketChannelConfig {

    private NioServerSocketChannelConfig(NioServerSocketChannel channel, ServerSocket javaSocket) {
        super(channel, javaSocket);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option instanceof NioChannelOption) {
            return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
        }
        return super.setOption(option, value);
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option instanceof NioChannelOption) {
            return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
        }
        return super.getOption(option);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), NioChannelOption.getOptions(jdkChannel()));
    }

    private ServerSocketChannel jdkChannel() {
        return ((NioServerSocketChannel) channel).javaChannel();
    }

}
