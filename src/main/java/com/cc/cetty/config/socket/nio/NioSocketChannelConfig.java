package com.cc.cetty.config.socket.nio;

import com.cc.cetty.channel.socket.NioSocketChannel;
import com.cc.cetty.config.option.ChannelOption;
import com.cc.cetty.config.option.NioChannelOption;
import com.cc.cetty.config.socket.DefaultSocketChannelConfig;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public final class NioSocketChannelConfig extends DefaultSocketChannelConfig {
    private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;
    public NioSocketChannelConfig(NioSocketChannel channel, Socket javaSocket) {
        super(channel, javaSocket);
        calculateMaxBytesPerGatheringWrite();
    }

    @Override
    public NioSocketChannelConfig setSendBufferSize(int sendBufferSize) {
        super.setSendBufferSize(sendBufferSize);
        calculateMaxBytesPerGatheringWrite();
        return this;
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if ( option instanceof NioChannelOption) {
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

    public void setMaxBytesPerGatheringWrite(int maxBytesPerGatheringWrite) {
        this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
    }

    public int getMaxBytesPerGatheringWrite() {
        return maxBytesPerGatheringWrite;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(getSendBufferSize() << 1);
        }
    }

    private SocketChannel jdkChannel() {
        return ((NioSocketChannel) channel).javaChannel();
    }
}